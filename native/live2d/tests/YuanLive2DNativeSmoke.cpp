#define NOMINMAX
#include "YuanLive2DNativeSupport.hpp"
#include "YuanLive2DModel.hpp"
#include "YuanLive2DGlState.hpp"

#include <CubismFramework.hpp>
#include <ICubismAllocator.hpp>
#include <Live2DCubismCore.h>
#include <Id/CubismIdManager.hpp>
#include <Physics/CubismPhysicsJson.hpp>
#include <GL/glew.h>
#include <array>
#include <cmath>
#include <cstdlib>
#include <cwctype>
#include <fstream>
#include <iostream>
#include <limits>
#include <memory>
#include <unordered_map>
#include <stdexcept>
#include <vector>
#include <windows.h>

namespace {
void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

void requireNear(float actual, float expected, const char* message) {
    if (std::abs(actual - expected) > 0.001f) throw std::runtime_error(message);
}

template<class Exception, class Function>
void requireThrows(Function&& function, const char* message) {
    try { function(); }
    catch (const Exception&) { return; }
    throw std::runtime_error(message);
}

class Allocator final : public Csm::ICubismAllocator {
public:
    void* Allocate(Csm::csmSizeType size) override { return std::malloc(size); }
    void Deallocate(void* memory) override { std::free(memory); }
    void* AllocateAligned(Csm::csmSizeType size, Csm::csmUint32 alignment) override {
        return _aligned_malloc(size, alignment);
    }
    void DeallocateAligned(void* memory) override { _aligned_free(memory); }
};

class GlContext final {
public:
    GlContext() {
        WNDCLASSW windowClass{};
        windowClass.lpfnWndProc = DefWindowProcW;
        windowClass.hInstance = GetModuleHandleW(nullptr);
        windowClass.lpszClassName = L"YuanLive2DSmoke";
        atom_ = RegisterClassW(&windowClass);
        if (!atom_) throw std::runtime_error("WGL smoke window class registration failed");
        window_ = CreateWindowExW(0, windowClass.lpszClassName, L"", WS_OVERLAPPEDWINDOW,
                0, 0, 1, 1, nullptr, nullptr, windowClass.hInstance, nullptr);
        if (!window_) throw std::runtime_error("WGL smoke window creation failed");
        dc_ = GetDC(window_);
        PIXELFORMATDESCRIPTOR format{sizeof(format), 1, PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER,
                PFD_TYPE_RGBA, 32, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 24, 8, 0,
                PFD_MAIN_PLANE, 0, 0, 0, 0};
        const int selected = ChoosePixelFormat(dc_, &format);
        if (!selected || !SetPixelFormat(dc_, selected, &format)) throw std::runtime_error("WGL smoke pixel format failed");
        HGLRC legacy = wglCreateContext(dc_);
        if (!legacy || !wglMakeCurrent(dc_, legacy)) throw std::runtime_error("WGL smoke bootstrap context creation failed");
        using CreateContext = HGLRC (WINAPI*)(HDC, HGLRC, const int*);
        auto createContext = reinterpret_cast<CreateContext>(wglGetProcAddress("wglCreateContextAttribsARB"));
        if (!createContext) throw std::runtime_error("WGL_ARB_create_context is unavailable");
        const int attributes[]{0x2091, 3, 0x2092, 3, 0x2094, 0x0001, 0x9126, 0x00000002, 0};
        context_ = createContext(dc_, nullptr, attributes);
        if (!context_ || !wglMakeCurrent(dc_, context_)) throw std::runtime_error("WGL 3.3 context creation failed");
        wglDeleteContext(legacy);
        glewExperimental = GL_TRUE;
        if (glewInit() != GLEW_OK) throw std::runtime_error("WGL smoke GLEW initialization failed");
        while (glGetError() != GL_NO_ERROR) {}
        YuanLive2DValidateGlFunctions();
    }

    ~GlContext() {
        wglMakeCurrent(nullptr, nullptr);
        if (context_) wglDeleteContext(context_);
        if (dc_ && window_) ReleaseDC(window_, dc_);
        if (window_) DestroyWindow(window_);
        if (atom_) UnregisterClassW(L"YuanLive2DSmoke", GetModuleHandleW(nullptr));
    }

private:
    ATOM atom_ = 0;
    HWND window_ = nullptr;
    HDC dc_ = nullptr;
    HGLRC context_ = nullptr;
};
}

int main(int argc, char** argv) {
    try {
    namespace fs = std::filesystem;
    const auto base = fs::temp_directory_path() / "yuan-live2d-native-smoke";
    fs::remove_all(base);
    fs::create_directories(base / "root");
    std::ofstream(base / "root" / "inside.txt") << "x";
    std::ofstream(base / "outside.txt") << "x";
    const auto root = YuanLive2DCanonicalRoot(base / "root");
    require(YuanLive2DResolveContained(root, "inside.txt") == fs::canonical(base / "root" / "inside.txt"),
            "contained path resolution failed");
    auto differentlyCasedRoot = root.wstring();
    for (auto& character : differentlyCasedRoot) character = static_cast<wchar_t>(std::towupper(character));
    require(YuanLive2DCanonicalContained(differentlyCasedRoot, base / "root" / "inside.txt")
            == fs::canonical(base / "root" / "inside.txt"), "case-insensitive containment failed");
    requireThrows<std::invalid_argument>([&] { YuanLive2DResolveContained(root, "../outside.txt"); },
                                         "parent escape was accepted");
    requireThrows<std::invalid_argument>([&] { YuanLive2DResolveContained(root, fs::canonical(base / "outside.txt")); },
                                         "absolute escape was accepted");

    std::int64_t handle = 1;
    require(YuanLive2DTakeHandle(handle) == 1 && handle == 2, "handle allocation failed");
    handle = std::numeric_limits<std::int64_t>::max();
    requireThrows<std::runtime_error>([&] { YuanLive2DTakeHandle(handle); }, "handle exhaustion was accepted");
    require(YuanLive2DBoundedUtf8Length("abc", 2) == 2, "ASCII bound failed");
    require(YuanLive2DBoundedUtf8Length("a\xE2\x82\xAC", 3) == 1, "UTF-8 bound split a code point");

    {
        GlContext context;
        Allocator allocator;
        Csm::CubismFramework::Option option{};
        option.LoadFileFunction = YuanLive2DLoadFile;
        option.ReleaseBytesFunction = YuanLive2DReleaseFile;
        require(Csm::CubismFramework::StartUp(&allocator, &option), "Cubism Framework startup failed");
        Csm::CubismFramework::Initialize();
        require(argc == 2, "Cubism SDK root argument is missing");
        const fs::path sdk = fs::u8path(argv[1]);
        const fs::path modelRoot = sdk / "Samples/Resources/Haru";
        const fs::path shaders = base / "FrameworkShaders";
        fs::copy(sdk / "Framework/src/Rendering/OpenGL/Shaders/Standard", shaders,
                fs::copy_options::recursive | fs::copy_options::overwrite_existing);
        YuanLive2DSetShaderRoot(shaders);
        Csm::csmSizeInt shaderSize = 0;
        Csm::csmByte* shader = YuanLive2DLoadFile("FrameworkShaders/VertShaderSrc.vert", &shaderSize);
        require(shader != nullptr && shaderSize > 0, "Cubism vertex shader loader failed");
        YuanLive2DReleaseFile(shader);
        require(YuanLive2DEstimateTextureBytes(modelRoot, modelRoot / "Haru.model3.json") > 0,
                "Haru texture estimate failed");
        require(YuanLive2DEstimateTextureBytes(modelRoot, modelRoot / "Haru.model3.json") < (256ULL << 20),
                "Haru texture estimate exceeded its budget");
        requireThrows<std::runtime_error>([&] {
            YuanLive2DEstimateTextureBytes(modelRoot, modelRoot / "missing.model3.json");
        }, "texture estimate for a missing manifest was accepted");
        const fs::path corruptManifest = base / "root" / "corrupt.model3.json";
        std::ofstream(corruptManifest) << "{not valid json";
        requireThrows<std::runtime_error>([&] {
            YuanLive2DEstimateTextureBytes(root, "corrupt.model3.json");
        }, "texture estimate for a corrupt manifest was accepted");
        GLuint vao = 0;
        glGenVertexArrays(1, &vao);
        glBindVertexArray(vao);
        YuanLive2DCheckGl("smoke VAO bind");
        YuanLive2DBeginGlOperation("smoke model creation");
        {
            YuanLive2DModel model(modelRoot, modelRoot / "Haru.model3.json", 1, 1, 256ULL << 20);
            const auto bounds = model.Bounds(854, 480, 100.0f, 200.0f, 240.0f);
            require(std::all_of(bounds.begin(), bounds.end(), [](float value) { return std::isfinite(value); }),
                    "model bounds were not finite");
            require(bounds[0] < bounds[2] && bounds[1] < bounds[3], "model bounds were not ordered");
            Live2D::Cubism::Core::csmVector2 sizePixels{}, originPixels{};
            float pixelsPerUnit = 0.0f;
            Live2D::Cubism::Core::csmReadCanvasInfo(
                    model.GetModel()->GetModel(), &sizePixels, &originPixels, &pixelsPerUnit);
            const float modelScale = 2.0f / (sizePixels.Y / pixelsPerUnit);
            const float left = -originPixels.X / pixelsPerUnit;
            const float right = (sizePixels.X - originPixels.X) / pixelsPerUnit;
            const float bottom = -originPixels.Y / pixelsPerUnit;
            const float top = (sizePixels.Y - originPixels.Y) / pixelsPerUnit;
            float contentMinX = std::numeric_limits<float>::infinity();
            float contentMinY = std::numeric_limits<float>::infinity();
            float contentMaxX = -std::numeric_limits<float>::infinity();
            float contentMaxY = -std::numeric_limits<float>::infinity();
            for (Csm::csmInt32 drawable = 0; drawable < model.GetModel()->GetDrawableCount(); ++drawable) {
                const auto* positions = model.GetModel()->GetDrawableVertexPositions(drawable);
                const Csm::csmInt32 vertexCount = model.GetModel()->GetDrawableVertexCount(drawable);
                for (Csm::csmInt32 vertex = 0; vertex < vertexCount; ++vertex) {
                    contentMinX = (std::min)(contentMinX, positions[vertex].X);
                    contentMaxX = (std::max)(contentMaxX, positions[vertex].X);
                    contentMinY = (std::min)(contentMinY, positions[vertex].Y);
                    contentMaxY = (std::max)(contentMaxY, positions[vertex].Y);
                }
            }
            require(contentMinX < contentMaxX && contentMinY < contentMaxY,
                    "Haru content extents were empty");
            requireNear(bounds[0], 100.0f + 240.0f * modelScale * contentMinX,
                    "Haru left bound ignored drawable content");
            requireNear(bounds[1], 200.0f - 240.0f * modelScale * contentMaxY,
                    "Haru top bound ignored drawable content");
            requireNear(bounds[2], 100.0f + 240.0f * modelScale * contentMaxX,
                    "Haru right bound ignored drawable content");
            requireNear(bounds[3], 200.0f - 240.0f * modelScale * contentMinY,
                    "Haru bottom bound ignored drawable content");
            require(bounds[0] > 100.0f + 240.0f * modelScale * left
                            && bounds[2] < 100.0f + 240.0f * modelScale * right
                            && bounds[1] > 200.0f - 240.0f * modelScale * top
                            && bounds[3] < 200.0f - 240.0f * modelScale * bottom,
                    "Haru content exceeded the canvas bounds");

            const auto offscreen = model.Bounds(854, 480, -1000.0f, -1000.0f, 240.0f);
            requireNear(offscreen[0], -1000.0f + 240.0f * modelScale * contentMinX,
                    "offscreen left bound was clipped to framebuffer");
            requireNear(offscreen[1], -1000.0f - 240.0f * modelScale * contentMaxY,
                    "offscreen top bound was clipped to framebuffer");
            requireNear(offscreen[2], -1000.0f + 240.0f * modelScale * contentMaxX,
                    "offscreen right bound was clipped to framebuffer");
            requireNear(offscreen[3], -1000.0f - 240.0f * modelScale * contentMinY,
                    "offscreen bottom bound was clipped to framebuffer");
            require(offscreen[2] < 0.0f && offscreen[3] < 0.0f,
                    "fully offscreen bounds must preserve negative extrema");

            const auto firstFrame = model.Draw(320, 180, 100.0f, 120.0f, 180.0f, 1.0f);
            require(firstFrame.texture != 0, "offscreen draw returned no texture");
            require(firstFrame.width == 320 && firstFrame.height == 180,
                    "offscreen draw returned the wrong dimensions");

            GLuint readFbo = 0;
            glGenFramebuffers(1, &readFbo);
            glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, firstFrame.texture, 0);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                    "returned output texture is not framebuffer-complete");
            std::vector<unsigned char> pixels(320 * 180 * 4);
            glReadPixels(0, 0, 320, 180, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
            bool hasAlpha = false;
            for (size_t i = 3; i < pixels.size(); i += 4) hasAlpha |= pixels[i] != 0;
            require(hasAlpha, "offscreen output contains no non-zero alpha");
            glDeleteFramebuffers(1, &readFbo);

            const auto resizedFrame = model.Draw(256, 144, 80.0f, 90.0f, 144.0f, 1.0f);
            require(resizedFrame.texture != 0 && resizedFrame.width == 256 && resizedFrame.height == 144,
                    "offscreen output did not resize");
            require(resizedFrame.texture != firstFrame.texture,
                    "resize must replace the output texture");

            {
                GLuint multiFbo = 0;
                glGenFramebuffers(1, &multiFbo);
                glBindFramebuffer(GL_FRAMEBUFFER, multiFbo);
                std::vector<unsigned char> multiPixels(256 * 144 * 4);
                int blankFrames = 0;
                for (int frame = 0; frame < 20; ++frame) {
                    YuanLive2DModel::TextureFrame f;
                    if (frame & 1) {
                        model.Update(1.0f / 60.0f);
                        f = model.Draw(256, 144, 80.0f, 90.0f, 144.0f, 1.0f);
                    } else {
                        f = model.Draw(256, 144, 80.0f, 90.0f, 144.0f, 1.0f);
                    }
                    require(f.texture != 0 && f.width == 256 && f.height == 144,
                            "multi-frame draw returned bad descriptor");
                    require(f.texture == resizedFrame.texture,
                            "multi-frame draw changed the output texture");
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, f.texture, 0);
                    require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                            "multi-frame output texture not framebuffer-complete");
                    glReadPixels(0, 0, 256, 144, GL_RGBA, GL_UNSIGNED_BYTE, multiPixels.data());
                    bool hasAlpha = false;
                    for (size_t i = 3; i < multiPixels.size(); i += 4)
                        hasAlpha |= multiPixels[i] != 0;
                    if (!hasAlpha) ++blankFrames;
                }
                require(blankFrames == 0,
                        "multi-frame output had blank frames");
                glDeleteFramebuffers(1, &multiFbo);
            }

            model.Control(.5f, -.25f, 1.0f, 0);
            model.Update(1.0f / 60.0f);
            const Csm::CubismIdHandle angleXId = Csm::CubismFramework::GetIdManager()->GetId("ParamAngleX");
            const Csm::CubismIdHandle angleYId = Csm::CubismFramework::GetIdManager()->GetId("ParamAngleY");
            const Csm::csmInt32 angleX = model.GetModel()->GetParameterIndex(angleXId);
            const Csm::csmInt32 angleY = model.GetModel()->GetParameterIndex(angleYId);
            require(angleX >= 0 && angleY >= 0, "Haru must expose gaze parameters");
            if (angleX >= 0) {
                const float min = model.GetModel()->GetParameterMinimumValue(angleX);
                const float max = model.GetModel()->GetParameterMaximumValue(angleX);
                const float expected = min + (.5f + 1.0f) * .5f * (max - min);
                requireNear(model.GetModel()->GetParameterValue(angleX), expected,
                        "head X must map the look input onto the parameter range");
            }
            if (angleY >= 0) {
                const float min = model.GetModel()->GetParameterMinimumValue(angleY);
                const float max = model.GetModel()->GetParameterMaximumValue(angleY);
                const float expected = min + (-.25f + 1.0f) * .5f * (max - min);
                requireNear(model.GetModel()->GetParameterValue(angleY), expected,
                        "head Y must map the look input onto the parameter range");
            }
            const Csm::csmInt32 bodyX = model.GetModel()->GetParameterIndex(
                    Csm::CubismFramework::GetIdManager()->GetId("ParamBodyAngleX"));
            const Csm::csmInt32 bodyY = model.GetModel()->GetParameterIndex(
                    Csm::CubismFramework::GetIdManager()->GetId("ParamBodyAngleY"));
            if (bodyX >= 0) {
                const float min = model.GetModel()->GetParameterMinimumValue(bodyX);
                const float max = model.GetModel()->GetParameterMaximumValue(bodyX);
                const float expected = min + (.25f + 1.0f) * .5f * (max - min); // body 取 look 一半
                requireNear(model.GetModel()->GetParameterValue(bodyX), expected,
                        "body X must follow the look input at half amplitude");
            }
            if (bodyY >= 0) {
                const float min = model.GetModel()->GetParameterMinimumValue(bodyY);
                const float max = model.GetModel()->GetParameterMaximumValue(bodyY);
                const float expected = min + (-.125f + 1.0f) * .5f * (max - min); // body 取 look 一半
                requireNear(model.GetModel()->GetParameterValue(bodyY), expected,
                        "body Y must follow the look input at half amplitude");
            }
            {
                const fs::path physicsFile = "Haru.physics3.json";
                const auto physicsBytes = YuanLive2DReadBytes(YuanLive2DResolveContained(modelRoot, physicsFile));
                Csm::CubismPhysicsJson physicsJson(physicsBytes.data(), static_cast<Csm::csmSizeInt>(physicsBytes.size()));
                const Csm::CubismIdHandle firstOutputId = physicsJson.GetSubRigCount() > 0 && physicsJson.GetOutputCount(0) > 0
                        ? physicsJson.GetOutputsDestinationId(0, 0) : nullptr;
                const Csm::csmInt32 physicsParam = firstOutputId == nullptr
                        ? -1 : model.GetModel()->GetParameterIndex(firstOutputId);
                if (physicsParam < 0) {
                    // Haru has no physics output parameters; keep only the no-crash assertion.
                    model.Control(0.0f, 0.0f, 2.0f, 0);
                    model.Update(1.0f / 60.0f);
                } else {
                    // A tiny delta freezes pendulum state so the assertions measure the
                    // amplitude multiplier instead of frame-to-frame sway.
                    const float stillDelta = 1.0e-6f;
                    model.Control(0.0f, 0.0f, 0.0f, 0);
                    model.Update(stillDelta);
                    requireNear(model.GetModel()->GetParameterValue(physicsParam), 0.0f,
                            "physics amplitude 0 must scale output to zero");
                    model.Control(0.0f, 0.0f, 2.0f, 0);
                    model.Update(stillDelta);
                    const float doubled = model.GetModel()->GetParameterValue(physicsParam);
                    require(std::abs(doubled) > 0.002f,
                            "Haru physics output must be non-zero at amplitude 2");
                    model.Control(0.0f, 0.0f, 1.0f, 0);
                    model.Update(stillDelta);
                    const float single = model.GetModel()->GetParameterValue(physicsParam);
                    requireNear(doubled, 2.0f * single,
                            "physics amplitude 2 must double output");
                }
            }
            {
                // Two identical Haru instances prove gaze reaches physics instead of
                // lagging one frame: after a shared settle, only the look input differs,
                // so the hair output delta cannot come from animation or breath drift.
                YuanLive2DModel neutral(modelRoot, modelRoot / "Haru.model3.json", 1, 1, 256ULL << 20);
                YuanLive2DModel look(modelRoot, modelRoot / "Haru.model3.json", 1, 1, 256ULL << 20);
                for (int frame = 0; frame < 60; ++frame) {
                    neutral.Control(0.0f, 0.0f, 1.0f, 0);
                    neutral.Update(1.0f / 60.0f);
                    look.Control(0.0f, 0.0f, 1.0f, 0);
                    look.Update(1.0f / 60.0f);
                }
                const Csm::csmInt32 hair = neutral.GetModel()->GetParameterIndex(
                        Csm::CubismFramework::GetIdManager()->GetId("ParamHairFront"));
                require(hair >= 0, "Haru must expose a physics-driven hair parameter");
                for (int frame = 0; frame < 60; ++frame) {
                    neutral.Control(0.0f, 0.0f, 1.0f, 0);
                    neutral.Update(1.0f / 60.0f);
                    look.Control(.5f, 0.0f, 1.0f, 0);
                    look.Update(1.0f / 60.0f);
                }
                require(std::abs(look.GetModel()->GetParameterValue(hair)
                                 - neutral.GetModel()->GetParameterValue(hair)) > 0.01f,
                        "gaze must drive physics outputs instead of lagging a frame");
                const Csm::csmInt32 eyeX = look.GetModel()->GetParameterIndex(
                        Csm::CubismFramework::GetIdManager()->GetId("ParamEyeBallX"));
                if (eyeX >= 0) {
                    const float min = look.GetModel()->GetParameterMinimumValue(eyeX);
                    const float max = look.GetModel()->GetParameterMaximumValue(eyeX);
                    const float expected = min + (.4f + 1.0f) * .5f * (max - min);
                    requireNear(look.GetModel()->GetParameterValue(eyeX), expected,
                            "eye X must follow the look input at 0.8 amplitude");
                }
                neutral.Close();
                look.Close();
            }
            model.Control(0.0f, 0.0f, 1.0f, 1);  // random motion pulse
            model.Update(1.0f / 60.0f);
            require(model.GetMotionManager() != nullptr
                    && model.GetMotionManager()->GetCubismMotionQueueEntries()->GetSize() > 0,
                    "random motion pulse must start an idle motion on Haru");
            model.Control(0.0f, 0.0f, 1.0f, 4);  // expression pulse
            model.Update(1.0f / 60.0f);
            require(model.GetExpressionManager() != nullptr
                    && model.GetExpressionManager()->GetCubismMotionQueueEntries()->GetSize() > 0,
                    "expression pulse must start an expression on Haru");
            model.Close();
        }
        {
            const fs::path fallbackRoot = base / "fallback";
            fs::create_directories(fallbackRoot);
            fs::copy(modelRoot / "Haru.moc3", fallbackRoot / "Haru.moc3",
                    fs::copy_options::overwrite_existing);
            fs::copy(modelRoot / "Haru.2048", fallbackRoot / "Haru.2048",
                    fs::copy_options::recursive | fs::copy_options::overwrite_existing);
            std::ofstream(fallbackRoot / "fallback.model3.json") << R"({
  "Version": 3,
  "FileReferences": {
    "Moc": "Haru.moc3",
    "Textures": [
      "Haru.2048/texture_00.png",
      "Haru.2048/texture_01.png"
    ]
  }
})";
            YuanLive2DModel fallback(fallbackRoot, fallbackRoot / "fallback.model3.json", 1, 1, 256ULL << 20);
            const Csm::CubismIdHandle angleXId = Csm::CubismFramework::GetIdManager()->GetId("ParamAngleX");
            const Csm::csmInt32 angleX = fallback.GetModel()->GetParameterIndex(angleXId);
            const float angleXBefore = angleX >= 0 ? fallback.GetModel()->GetParameterValue(angleX) : 0.0f;
            fallback.Control(0.0f, 0.0f, 1.0f, 1);
            fallback.Update(1.0f / 60.0f);
            fallback.Update(1.0f / 60.0f);
            // gaze=0 isolates sway from breath: the fallback writes ParamAngleX = gazeX*15 + swayX
            // after the SDK breath effect, so the multi-frame delta is the pure swayX contribution
            // while ParamBreath carries the breath variation.
            for (int frame = 0; frame < 30; ++frame) fallback.Update(1.0f / 60.0f);
            if (angleX >= 0) {
                require(std::abs(fallback.GetModel()->GetParameterValue(angleX) - angleXBefore) > 0.5f,
                        "programmatic fallback must move ParamAngleX");
            }
            // A tiny delta suppresses sway change, so a full look input must still
            // leave ParamAngleX clearly positive after sway is layered on top.
            fallback.Control(1.0f, 0.0f, 1.0f, 0);
            fallback.Update(1.0e-4f);
            if (angleX >= 0) {
                require(fallback.GetModel()->GetParameterValue(angleX) > 20.0f,
                        "programmatic fallback must preserve gaze when sway applies");
            }
            // A model without ParamAngleX keeps only the no-crash fallback coverage above.
            fallback.Close();
        }
        glDeleteVertexArrays(1, &vao);
        Csm::CubismFramework::Dispose();
        Csm::CubismFramework::CleanUp();
        YuanLive2DClearShaderRoot();
    }

    struct Resource {
        explicit Resource(int* count) : destroyed(count) {}
        int* destroyed;
        ~Resource() { ++*destroyed; }
    };
    std::unordered_map<std::int64_t, std::unique_ptr<Resource>> published;
    int destroyed = 0;
    auto local = std::make_unique<Resource>(&destroyed);
    requireThrows<std::runtime_error>([&] {
        YuanLive2DCommitCreate(local, [] { throw std::runtime_error("post-create check"); },
                [&](auto resource) { published.emplace(1, std::move(resource)); });
    }, "failed create commit check was accepted");
    require(published.empty() && local && destroyed == 0, "failed create published or lost local ownership");
    local.reset();
    require(destroyed == 1, "local create rollback did not release resource");

    {
        YuanLive2DFinalCleanupState cleanup{true, true, 7};
        int model = 0, framework = 0, vao = 0, check = 0;
        try {
            YuanLive2DAdvanceFinalCleanup(cleanup,
                    [&] { ++model; throw 17; }, [&] { ++framework; },
                    [&](std::uint32_t) { ++vao; }, [&] { ++check; });
            throw std::runtime_error("non-standard model cleanup fault was not reported");
        } catch (int value) { require(value == 17, "wrong non-standard cleanup exception"); }
        require(model == 1 && framework == 0 && vao == 0 && check == 0,
                "model failure must retain dependent Framework, VAO, and check stages");
        require(cleanup.modelOwned && cleanup.frameworkActive && cleanup.vao == 7 && !cleanup.checked,
                "failed model cleanup advanced dependent progress");
        YuanLive2DAdvanceFinalCleanup(cleanup, [&] { ++model; }, [&] { ++framework; },
                [&](std::uint32_t) { ++vao; }, [&] { ++check; });
        require(YuanLive2DCanErase(cleanup) && model == 2 && framework == 1 && vao == 1 && check == 1,
                "model cleanup retry did not complete pending stages");
    }

    {
        YuanLive2DFinalCleanupState cleanup{true, true, 7};
        int model = 0, framework = 0, vao = 0, check = 0;
        requireThrows<std::runtime_error>([&] {
            YuanLive2DAdvanceFinalCleanup(cleanup, [&] { ++model; },
                    [&] { ++framework; throw std::runtime_error("framework first"); },
                    [&](std::uint32_t) { ++vao; }, [&] { ++check; });
        }, "Framework cleanup fault was not reported");
        require(!cleanup.modelOwned && cleanup.frameworkActive && cleanup.vao == 0 && cleanup.checked,
                "safe later stages did not progress after Framework failure");
        YuanLive2DAdvanceFinalCleanup(cleanup, [&] { ++model; }, [&] { ++framework; },
                [&](std::uint32_t) { ++vao; }, [&] { ++check; });
        require(YuanLive2DCanErase(cleanup) && model == 1 && framework == 2 && vao == 1 && check == 1,
                "successful later stages repeated after Framework retry");
    }

    {
        YuanLive2DFinalCleanupState cleanup{true, true, 7};
        int model = 0, framework = 0, vao = 0, check = 0;
        try {
            YuanLive2DAdvanceFinalCleanup(cleanup, [&] { ++model; }, [&] { ++framework; },
                    [&](std::uint32_t) { ++vao; throw std::runtime_error("vao first"); },
                    [&] { ++check; throw std::runtime_error("check second"); });
            throw std::runtime_error("VAO cleanup fault was not reported");
        } catch (const std::runtime_error& error) {
            require(std::string(error.what()) == "vao first", "cleanup did not preserve the first exception");
        }
        require(!cleanup.modelOwned && !cleanup.frameworkActive && cleanup.vao == 7 && !cleanup.checked,
                "failed independent stages advanced progress");
        YuanLive2DAdvanceFinalCleanup(cleanup, [&] { ++model; }, [&] { ++framework; },
                [&](std::uint32_t) { ++vao; }, [&] { ++check; });
        require(YuanLive2DCanErase(cleanup) && model == 1 && framework == 1 && vao == 2 && check == 2,
                "VAO/check retry repeated completed stages");
    }

    {
        YuanLive2DFinalCleanupState cleanup{false, false, 7};
        int deleteAttempts = 0;
        requireThrows<std::runtime_error>([&] {
            YuanLive2DAdvanceFinalCleanup(cleanup, [] {}, [] {},
                    [&](std::uint32_t) {
                        ++deleteAttempts; // deletion action completed, delayed GL check failed
                        throw std::runtime_error("VAO delete GL check");
                    }, [] {});
        }, "delayed VAO deletion check fault was not reported");
        require(cleanup.vao == 7 && deleteAttempts == 1,
                "failed VAO deletion check must retain retryable progress");
        YuanLive2DAdvanceFinalCleanup(cleanup, [] {}, [] {},
                [&](std::uint32_t) { ++deleteAttempts; }, [] {});
        require(cleanup.vao == 0 && deleteAttempts == 2 && YuanLive2DCanErase(cleanup),
                "VAO deletion retry did not clear progress after checked success");
    }

    {
        YuanLive2DFinalCleanupState cleanup{false, false, 0};
        int checks = 0;
        requireThrows<std::runtime_error>([&] {
            YuanLive2DAdvanceFinalCleanup(cleanup, [] {}, [] {}, [](std::uint32_t) {},
                    [&] { ++checks; throw std::runtime_error("check"); });
        }, "final cleanup check fault was not reported");
        YuanLive2DAdvanceFinalCleanup(cleanup, [] {}, [] {}, [](std::uint32_t) {}, [&] { ++checks; });
        require(YuanLive2DCanErase(cleanup) && checks == 2, "final cleanup check was not retryable");
    }
    fs::remove_all(base);
    return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
