#include "YuanLive2DGlState.hpp"
#include "YuanLive2DModel.hpp"
#include "YuanLive2DNativeSupport.hpp"

#include <CubismFramework.hpp>
#include <ICubismAllocator.hpp>
#include <Live2DCubismCore.h>
#include <GL/glew.h>
#include <jni.h>
#include <array>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string_view>
#include <thread>
#include <unordered_map>
#include <windows.h>

namespace {
constexpr size_t MaxErrorBytes = 4096;
thread_local std::array<char, MaxErrorBytes + 1> lastError{};
thread_local size_t lastErrorSize = 0;
std::mutex runtimeMutex;
std::unordered_map<jlong, std::unique_ptr<YuanLive2DModel>> models;
std::unordered_map<jlong, YuanLive2DFinalCleanupState> finalCleanupStates;
jlong nextHandle = 1;
std::thread::id renderThread;
HGLRC renderContext = nullptr;
GLuint cubismVao = 0;
std::filesystem::path activeShaderRoot;
thread_local bool cubismError = false;
thread_local bool cubismOperationActive = false;

struct JniPendingException {};

class CubismAllocator final : public Csm::ICubismAllocator {
public:
    void* Allocate(Csm::csmSizeType size) override { return std::malloc(size); }
    void Deallocate(void* memory) override { std::free(memory); }
    void* AllocateAligned(Csm::csmSizeType size, Csm::csmUint32 alignment) override { return _aligned_malloc(size, alignment); }
    void DeallocateAligned(void* memory) override { _aligned_free(memory); }
};

CubismAllocator allocator;
Csm::CubismFramework::Option option{};

void clearError() noexcept {
    lastErrorSize = 0;
    lastError[0] = '\0';
}

void setError(std::string_view message) noexcept {
    lastErrorSize = YuanLive2DBoundedUtf8Length(message, MaxErrorBytes);
    std::memcpy(lastError.data(), message.data(), lastErrorSize);
    lastError[lastErrorSize] = '\0';
}

void logCubism(const char* message) {
    if (!message) return;
    OutputDebugStringA(message);
    if (!cubismOperationActive) return;
    if (std::strstr(message, "[E]")) { cubismError = true; return; }
    for (const char* start = message; *start; ++start) {
        const char* word = "error";
        const char* cursor = start;
        while (*cursor && *word && std::tolower(static_cast<unsigned char>(*cursor)) == *word) { ++cursor; ++word; }
        if (!*word) { cubismError = true; return; }
        word = "fail";
        cursor = start;
        while (*cursor && *word && std::tolower(static_cast<unsigned char>(*cursor)) == *word) { ++cursor; ++word; }
        if (!*word) { cubismError = true; return; }
    }
}

class CubismOperation {
public:
    CubismOperation() { cubismError = false; cubismOperationActive = true; }
    ~CubismOperation() { cubismOperationActive = false; }
    void Finish() {
        cubismOperationActive = false;
        if (cubismError) throw std::runtime_error("Cubism reported an error; see native debug output");
    }
};

void requireCoreProfileBuffers() {
    GLint major = 0, minor = 0;
    glGetIntegerv(GL_MAJOR_VERSION, &major);
    glGetIntegerv(GL_MINOR_VERSION, &minor);
    YuanLive2DCheckGl("OpenGL version query");
    if (major > 3 || (major == 3 && minor >= 2)) {
        GLint profile = 0;
        glGetIntegerv(GL_CONTEXT_PROFILE_MASK, &profile);
        YuanLive2DCheckGl("OpenGL profile query");
        if (!(profile & (GL_CONTEXT_CORE_PROFILE_BIT | GL_CONTEXT_COMPATIBILITY_PROFILE_BIT)))
            throw std::runtime_error("OpenGL context has no recognized core or compatibility profile");
    }
    GLint flags = 0;
    glGetIntegerv(GL_CONTEXT_FLAGS, &flags);
    YuanLive2DCheckGl("OpenGL context flags query");
    if (major < 3) throw std::runtime_error("OpenGL 3.0 or newer is required for Cubism VBO/EBO rendering");
}

std::filesystem::path javaPath(JNIEnv* env, jstring value, const char* name) {
    if (!value) throw std::invalid_argument(std::string(name) + " is null");
    const jsize length = env->GetStringLength(value);
    if (env->ExceptionCheck()) throw JniPendingException{};
    std::wstring wide(static_cast<size_t>(length), L'\0');
    if (length) env->GetStringRegion(value, 0, length, reinterpret_cast<jchar*>(wide.data()));
    if (env->ExceptionCheck()) throw JniPendingException{};
    return std::filesystem::path(wide);
}

HGLRC currentContext() {
    HGLRC context = wglGetCurrentContext();
    if (!context) throw std::runtime_error("No current WGL context");
    return context;
}

void requireOwner(HGLRC context) {
    if (renderThread != std::this_thread::get_id())
        throw std::runtime_error("Live2D native calls must stay on one render thread");
    if (renderContext != context)
        throw std::runtime_error("Live2D native calls must use the creation WGL context");
}

bool samePath(const std::filesystem::path& left, const std::filesystem::path& right) {
    return _wcsicmp(left.c_str(), right.c_str()) == 0;
}

void initializeGlew() {
    glewExperimental = GL_TRUE;
    if (glewInit() != GLEW_OK) throw std::runtime_error("GLEW initialization failed");
    for (GLenum artifact = glGetError(); artifact != GL_NO_ERROR; artifact = glGetError())
        if (artifact != GL_INVALID_ENUM) throw std::runtime_error("Unexpected OpenGL error from glewInit");
    YuanLive2DCheckGl("after glewInit");
    YuanLive2DValidateGlFunctions();
    requireCoreProfileBuffers();
    if (!GLEW_VERSION_3_0 || !glGenVertexArrays || !glBindVertexArray || !glDeleteVertexArrays
            || !glGenBuffers || !glDeleteBuffers || !glBindBuffer || !glBufferData
            || !glGenFramebuffers || !glBindFramebuffer || !glCheckFramebufferStatus
            || !glGenerateMipmap || !glCreateProgram || !glCreateShader || !glBlendFuncSeparate)
        throw std::runtime_error("Required OpenGL 3.0 functions are unavailable");
}

void startFramework(const std::filesystem::path& shaderRoot) {
    if (Csm::CubismFramework::IsInitialized()) {
        if (!samePath(activeShaderRoot, shaderRoot)) throw std::runtime_error("Shader root differs from active Framework root");
        return;
    }
    YuanLive2DSetShaderRoot(shaderRoot);
    activeShaderRoot = shaderRoot;
    option.LogFunction = logCubism;
    option.LoggingLevel = Csm::CubismFramework::Option::LogLevel_Warning;
    option.LoadFileFunction = YuanLive2DLoadFile;
    option.ReleaseBytesFunction = YuanLive2DReleaseFile;
    if (!Csm::CubismFramework::StartUp(&allocator, &option)) {
        YuanLive2DClearShaderRoot();
        activeShaderRoot.clear();
        throw std::runtime_error("Cubism Framework startup failed");
    }
    try {
        Csm::CubismFramework::Initialize();
        if (!Csm::CubismFramework::IsInitialized()) throw std::runtime_error("Cubism Framework initialization failed");
    } catch (...) {
        if (Csm::CubismFramework::IsInitialized()) Csm::CubismFramework::Dispose();
        Csm::CubismFramework::CleanUp();
        YuanLive2DClearShaderRoot();
        activeShaderRoot.clear();
        throw;
    }
}

void stopFramework() {
    if (Csm::CubismFramework::IsInitialized()) Csm::CubismFramework::Dispose();
    if (Csm::CubismFramework::IsStarted()) Csm::CubismFramework::CleanUp();
    YuanLive2DClearShaderRoot();
    activeShaderRoot.clear();
}

template<class Result, class Function>
Result jniCall(Result failure, Function&& function) noexcept {
    try {
        clearError();
        return function();
    } catch (const JniPendingException&) {
        // Preserve JVM exceptions such as OutOfMemoryError; JNI returns with the exception pending.
    } catch (const std::exception& error) {
        setError(error.what());
    } catch (...) {
        setError("Unknown native exception");
    }
    return failure;
}

constexpr jint RenderSkipped = 0;
constexpr jint RenderDrawnWithoutUpdate = 1;
constexpr jint RenderDrawnWithUpdate = 2;
constexpr jint RenderFailedAfterUpdate = 3;
constexpr jint RenderFailedBeforeUpdate = -1;

jlongArray textureFrame(JNIEnv* env, jint status, const YuanLive2DModel::TextureFrame* frame) {
    const jlong errorCode = status < 0 || status == RenderFailedAfterUpdate ? 1 : 0;
    const jlong values[5] = {
        status,
        frame ? static_cast<jlong>(frame->texture) : 0,
        frame ? static_cast<jlong>(frame->width) : 0,
        frame ? static_cast<jlong>(frame->height) : 0
        ,
        errorCode
    };
    jlongArray result = env->NewLongArray(5);
    if (!result || env->ExceptionCheck()) throw JniPendingException{};
    env->SetLongArrayRegion(result, 0, 5, values);
    if (env->ExceptionCheck()) throw JniPendingException{};
    return result;
}

template<class Function>
jlongArray renderFrameJniCall(JNIEnv* env, Function&& function) noexcept {
    bool updated = false;
    try {
        clearError();
        return function(updated);
    } catch (const JniPendingException&) {
    } catch (const std::exception& error) {
        setError(error.what());
    } catch (...) {
        setError("Unknown native exception");
    }
    if (env->ExceptionCheck()) return nullptr;
    try {
        return textureFrame(env, updated ? RenderFailedAfterUpdate : RenderFailedBeforeUpdate, nullptr);
    } catch (const JniPendingException&) {
        return nullptr;
    }
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_version(JNIEnv*, jclass) {
    return jniCall<jlong>(0, [] { return static_cast<jlong>(Live2D::Cubism::Core::csmGetVersion()); });
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_create(JNIEnv* env, jclass, jstring root, jstring json, jstring shaders,
                                                 jlong textureMemoryBudgetBytes) {
    return jniCall<jlong>(0, [&] {
        std::lock_guard lock(runtimeMutex);
        const HGLRC context = currentContext();
        const auto modelRoot = YuanLive2DCanonicalRoot(javaPath(env, root, "modelRoot"));
        const auto modelJson = YuanLive2DCanonicalContained(modelRoot, javaPath(env, json, "modelJson"));
        const auto shaderRoot = YuanLive2DCanonicalRoot(javaPath(env, shaders, "shaderRoot"));
        if (textureMemoryBudgetBytes <= 0) throw std::invalid_argument("Texture memory budget must be positive");
        const bool first = models.empty();
        if (!first) {
            requireOwner(context);
            if (!Csm::CubismFramework::IsInitialized())
                throw std::runtime_error("Live2D native cleanup must finish before creating another model");
            if (!samePath(activeShaderRoot, shaderRoot)) throw std::runtime_error("Shader root differs from active Framework root");
        }

        YuanLive2DBeginGlOperation("create");
        YuanLive2DGlState operationState;
        models.reserve(models.size() + 1);
        GLuint localVao = cubismVao;
        std::unique_ptr<YuanLive2DModel> model;
        try {
            {
                YuanLive2DGlState state;
                if (first) {
                    initializeGlew();
                    startFramework(shaderRoot);
                    glGenVertexArrays(1, &localVao);
                    if (!localVao) throw std::runtime_error("Cubism VAO allocation failed");
                }
                glBindVertexArray(localVao);
                YuanLive2DCheckGl("Cubism VAO bind");
                CubismOperation operation;
                model = std::make_unique<YuanLive2DModel>(modelRoot, modelJson, 1, 1,
                        static_cast<std::uint64_t>(textureMemoryBudgetBytes));
                operation.Finish();
            }
            YuanLive2DCheckGl("create state restoration");
            const jlong createdHandle = static_cast<jlong>(YuanLive2DTakeHandle(nextHandle));
            std::unordered_map<jlong, std::unique_ptr<YuanLive2DModel>> staged;
            staged.emplace(createdHandle, nullptr);
            YuanLive2DCommitCreate(model, [] {}, [&](auto owned) {
                staged.at(createdHandle) = std::move(owned);
            });
            models.insert(staged.extract(createdHandle));
            if (first) {
                renderThread = std::this_thread::get_id();
                renderContext = context;
                cubismVao = localVao;
            }
            return createdHandle;
        } catch (...) {
            std::exception_ptr firstFailure = std::current_exception();
            try {
                if (localVao) glBindVertexArray(localVao);
                if (model) { model->Close(); model.reset(); }
            } catch (...) {}
            if (first) {
                try { stopFramework(); } catch (...) {}
                if (localVao) {
                    try { glDeleteVertexArrays(1, &localVao); YuanLive2DCheckGl("create rollback VAO destruction"); localVao = 0; }
                    catch (...) {}
                }
                renderThread = {};
                renderContext = nullptr;
                cubismVao = 0;
            }
            std::rethrow_exception(firstFailure);
        }
    });
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_render(JNIEnv* env, jclass, jlong handle, jfloat deltaSeconds,
                                                 jint width, jint height, jfloat x, jfloat y,
                                                 jfloat scale, jfloat opacity, jboolean updateModel) {
    return renderFrameJniCall(env, [&](bool& updated) {
        std::lock_guard lock(runtimeMutex);
        requireOwner(currentContext());
        auto found = models.find(handle);
        if (found == models.end()) throw std::invalid_argument("Invalid Live2D model handle");
        if (!found->second) throw std::runtime_error("Live2D model is pending destruction cleanup");
        const bool update = updateModel != JNI_FALSE;
        if (update) found->second->ValidateUpdate(deltaSeconds);
        found->second->ValidateDraw(width, height, x, y, scale, opacity);
        YuanLive2DBeginGlOperation("render");
        YuanLive2DModel::TextureFrame frame{};
        {
            YuanLive2DGlState state;
            glBindVertexArray(cubismVao);
            YuanLive2DCheckGl("Cubism VAO bind");
            CubismOperation operation;
            if (update) {
                updated = true;
                found->second->Update(deltaSeconds);
            }
            frame = found->second->Draw(width, height, x, y, scale, opacity);
            operation.Finish();
        }
        YuanLive2DCheckGl("render state restoration");
        return textureFrame(env, update ? RenderDrawnWithUpdate : RenderDrawnWithoutUpdate, &frame);
    });
}


extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_bounds(JNIEnv* env, jclass, jlong handle, jint width, jint height,
                                                 jfloat x, jfloat y, jfloat scale) {
    return jniCall<jfloatArray>(nullptr, [&] {
        std::lock_guard lock(runtimeMutex);
        requireOwner(currentContext());
        auto found = models.find(handle);
        if (found == models.end()) throw std::invalid_argument("Invalid Live2D model handle");
        if (!found->second) throw std::runtime_error("Live2D model is pending destruction cleanup");
        const auto bounds = found->second->Bounds(width, height, x, y, scale);
        jfloatArray result = env->NewFloatArray(4);
        if (!result || env->ExceptionCheck()) throw JniPendingException{};
        env->SetFloatArrayRegion(result, 0, 4, bounds.data());
        if (env->ExceptionCheck()) throw JniPendingException{};
        return result;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_control(JNIEnv*, jclass, jlong handle,
        jfloat gazeX, jfloat gazeY, jfloat physicsAmplitude, jint flags) {
    try {
        clearError();
        std::lock_guard lock(runtimeMutex);
        requireOwner(currentContext());
        auto found = models.find(handle);
        if (found == models.end()) throw std::invalid_argument("Invalid Live2D model handle");
        if (!found->second) throw std::runtime_error("Live2D model is pending destruction cleanup");
        found->second->Control(gazeX, gazeY, physicsAmplitude, static_cast<int>(flags));
    } catch (const std::exception& error) {
        setError(error.what());
    } catch (...) {
        setError("Unknown native exception");
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_estimate(JNIEnv* env, jclass, jstring root, jstring json, jstring shaders) {
    return jniCall<jlong>(0, [&] {
        std::lock_guard lock(runtimeMutex);
        const auto modelRoot = YuanLive2DCanonicalRoot(javaPath(env, root, "modelRoot"));
        const auto modelJson = YuanLive2DCanonicalContained(modelRoot, javaPath(env, json, "modelJson"));
        const auto shaderRoot = YuanLive2DCanonicalRoot(javaPath(env, shaders, "shaderRoot"));
        startFramework(shaderRoot);
        return static_cast<jlong>(YuanLive2DEstimateTextureBytes(modelRoot, modelJson));
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_destroy(JNIEnv*, jclass, jlong handle) {
    return jniCall<jboolean>(JNI_FALSE, [&] {
        if (!handle) return JNI_TRUE;
        std::lock_guard lock(runtimeMutex);
        requireOwner(currentContext());
        auto found = models.find(handle);
        if (found == models.end()) throw std::invalid_argument("Invalid Live2D model handle");
        YuanLive2DBeginGlOperation("destroy");
        YuanLive2DGlState operationState;
        const bool last = models.size() == 1;
        if (last) {
            auto [cleanupEntry, inserted] = finalCleanupStates.try_emplace(handle,
                    YuanLive2DFinalCleanupState{found->second != nullptr,
                            Csm::CubismFramework::IsStarted(), cubismVao});
            auto& cleanup = cleanupEntry->second;
            YuanLive2DAdvanceFinalCleanup(cleanup,
                    [&] {
                        YuanLive2DGlState state;
                        glBindVertexArray(cubismVao);
                        CubismOperation operation;
                        found->second->Close();
                        operation.Finish();
                        YuanLive2DCheckGl("model destruction");
                        found->second.reset();
                    },
                    [&] { stopFramework(); },
                    [&](std::uint32_t vao) {
                        GLuint value = static_cast<GLuint>(vao);
                        glDeleteVertexArrays(1, &value);
                        YuanLive2DCheckGl("final VAO destruction");
                        cubismVao = 0;
                    },
                    [&] { YuanLive2DCheckGl("final cleanup integration state"); });
            if (!YuanLive2DCanErase(cleanup)) throw std::runtime_error("Final Live2D cleanup is incomplete");
            finalCleanupStates.erase(cleanupEntry);
            models.erase(found);
            renderThread = {};
            renderContext = nullptr;
        } else {
            {
                YuanLive2DGlState state;
                glBindVertexArray(cubismVao);
                CubismOperation operation;
                found->second->Close();
                operation.Finish();
                YuanLive2DCheckGl("model destruction");
            }
            YuanLive2DCheckGl("destroy state restoration");
            found->second.reset();
            models.erase(found);
        }
        return JNI_TRUE;
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yuan_live2d_client_live2d_Live2DNative_lastError(JNIEnv* env, jclass) {
    try {
        if (!lastErrorSize) return env->NewStringUTF("");
        const int count = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, lastError.data(), static_cast<int>(lastErrorSize), nullptr, 0);
        if (!count) return env->NewStringUTF("Native error text was not valid UTF-8");
        std::wstring wide(static_cast<size_t>(count), L'\0');
        MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, lastError.data(), static_cast<int>(lastErrorSize), wide.data(), count);
        return env->NewString(reinterpret_cast<const jchar*>(wide.data()), count);
    } catch (const std::exception& error) {
        setError(error.what());
    } catch (...) {
        setError("Unknown native exception");
    }
    return nullptr;
}
