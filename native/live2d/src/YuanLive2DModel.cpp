#include "YuanLive2DModel.hpp"
#include "YuanLive2DGlState.hpp"
#include "YuanLive2DNativeSupport.hpp"

#include <Effect/CubismBreath.hpp>
#include <Effect/CubismEyeBlink.hpp>
#include <Id/CubismIdManager.hpp>
#include <Live2DCubismCore.h>
#include <Math/CubismMatrix44.hpp>
#include <Motion/CubismExpressionMotion.hpp>
#include <Motion/CubismMotion.hpp>
#include <Physics/CubismPhysicsJson.hpp>
#include <Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp>
#include <algorithm>
#include <climits>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <limits>
#include <memory>
#include <stdexcept>
#include <cstdint>
#include <sstream>
#include <windows.h>
#include <stb_image.h>

namespace fs = std::filesystem;
namespace {
fs::path shaderRoot;

fs::path shaderPath(const std::string& requested) {
    fs::path relative = fs::u8path(requested);
    if (relative.is_absolute()) throw std::invalid_argument("Absolute shader path is forbidden");
    for (const auto& component : relative)
        if (component == "..") throw std::invalid_argument("Shader path escapes root");
    if (relative.begin() != relative.end() && (*relative.begin()).u8string() == "FrameworkShaders"
            && shaderRoot.filename() == L"FrameworkShaders")
        relative = relative.lexically_relative("FrameworkShaders");
    return YuanLive2DResolveContained(shaderRoot, relative);
}

std::uint64_t mipChainBytes(std::uint64_t width, std::uint64_t height) {
    std::uint64_t level = width * height * 4;
    std::uint64_t textureBytes = 0;
    for (;;) {
        if (textureBytes > UINT64_MAX - level) throw std::runtime_error("Texture memory estimate overflow");
        textureBytes += level;
        if (width == 1 && height == 1) break;
        width = (std::max)(std::uint64_t{1}, width / 2);
        height = (std::max)(std::uint64_t{1}, height / 2);
        level = width * height * 4;
    }
    return textureBytes;
}

std::uint64_t estimateTextureBytes(Csm::CubismModelSettingJson& setting,
                                   const std::filesystem::path& modelHome) {
    std::uint64_t estimatedBytes = 0;
    for (Csm::csmInt32 i = 0; i < setting.GetTextureCount(); ++i) {
        const char* name = setting.GetTextureFileName(i);
        if (!name || !*name) throw std::runtime_error("Manifest contains an empty texture path");
        auto encoded = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome, fs::u8path(name)));
        if (encoded.size() > INT_MAX) throw std::runtime_error("PNG file is too large to inspect");
        int width = 0, height = 0, channels = 0;
        if (!stbi_info_from_memory(encoded.data(), static_cast<int>(encoded.size()), &width, &height, &channels))
            throw std::runtime_error(std::string("PNG info failed: ") + stbi_failure_reason());
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192)
            throw std::runtime_error("PNG dimensions must be within 8192 x 8192");
        std::uint64_t textureBytes = mipChainBytes(static_cast<std::uint64_t>(width),
                                                   static_cast<std::uint64_t>(height));
        if (estimatedBytes > UINT64_MAX - textureBytes) throw std::runtime_error("Texture memory estimate overflow");
        estimatedBytes += textureBytes;
    }
    return estimatedBytes;
}

struct TextureOwner {
    GLuint id = 0;
    ~TextureOwner() { if (id) glDeleteTextures(1, &id); }
};

Csm::CubismMatrix44 drawMatrix(int framebufferWidth, int framebufferHeight,
                               float x, float y, float scale, Csm::CubismMatrix44* modelMatrix) {
    Csm::CubismMatrix44 matrix;
    auto* values = matrix.GetArray();
    values[0] = 2.0f * scale / framebufferWidth;
    values[5] = 2.0f * scale / framebufferHeight;
    values[12] = -1.0f + 2.0f * x / framebufferWidth;
    values[13] = 1.0f - 2.0f * y / framebufferHeight;
    matrix.MultiplyByMatrix(modelMatrix);
    return matrix;
}
}

void YuanLive2DSetShaderRoot(const fs::path& root) { shaderRoot = root; }
void YuanLive2DClearShaderRoot() { shaderRoot.clear(); }

Csm::csmByte* YuanLive2DLoadFile(const std::string path, Csm::csmSizeInt* size) {
    if (!size) return nullptr;
    try {
        auto bytes = YuanLive2DReadBytes(shaderPath(path));
        auto* result = new Csm::csmByte[bytes.size()];
        std::memcpy(result, bytes.data(), bytes.size());
        *size = static_cast<Csm::csmSizeInt>(bytes.size());
        return result;
    } catch (...) {
        *size = 0;
        return nullptr;
    }
}

void YuanLive2DReleaseFile(Csm::csmByte* bytes) { delete[] bytes; }

YuanLive2DModel::YuanLive2DModel(fs::path modelHome, fs::path manifest,
                                 int framebufferWidth, int framebufferHeight, std::uint64_t textureMemoryBudgetBytes)
    : modelHome_(std::move(modelHome)), manifest_(std::move(manifest)),
      textureMemoryBudgetBytes_(textureMemoryBudgetBytes) {
    try {
        auto json = YuanLive2DReadBytes(YuanLive2DCanonicalContained(modelHome_, manifest_));
        setting_ = std::make_unique<Csm::CubismModelSettingJson>(json.data(), static_cast<Csm::csmSizeInt>(json.size()));
        if (!setting_ || !setting_->GetModelFileName()[0]) throw std::runtime_error("Manifest has no Moc file");

        auto moc = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(setting_->GetModelFileName())));
        LoadModel(moc.data(), static_cast<Csm::csmSizeInt>(moc.size()), true);
        if (!_model || !_modelMatrix) throw std::runtime_error("Cubism failed to load Moc model");

        const Csm::csmInt32 idleMotionCount = setting_->GetMotionCount("Idle");
        for (Csm::csmInt32 i = 0; i < idleMotionCount; ++i) {
            const char* file = setting_->GetMotionFileName("Idle", i);
            if (!file || !*file) continue;
            try {
                auto bytes = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(file)));
                Csm::CubismMotion* motion = Csm::CubismMotion::Create(
                        bytes.data(), static_cast<Csm::csmSizeInt>(bytes.size()));
                if (motion) idleMotions_.PushBack(motion);
            } catch (const std::exception& error) {
                OutputDebugStringA((std::string("YuanLive2D idle motion load skipped: ") + error.what() + "\n").c_str());
            }
        }
        const Csm::csmInt32 expressionCount = setting_->GetExpressionCount();
        for (Csm::csmInt32 i = 0; i < expressionCount; ++i) {
            const char* file = setting_->GetExpressionFileName(i);
            if (!file || !*file) continue;
            try {
                auto bytes = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(file)));
                Csm::CubismExpressionMotion* expression = Csm::CubismExpressionMotion::Create(
                        bytes.data(), static_cast<Csm::csmSizeInt>(bytes.size()));
                if (expression) expressions_.PushBack(expression);
            } catch (const std::exception& error) {
                OutputDebugStringA((std::string("YuanLive2D expression load skipped: ") + error.what() + "\n").c_str());
            }
        }
        const Csm::csmInt32 tapBodyMotionCount = setting_->GetMotionCount("TapBody");
        for (Csm::csmInt32 i = 0; i < tapBodyMotionCount; ++i) {
            const char* file = setting_->GetMotionFileName("TapBody", i);
            if (!file || !*file) continue;
            try {
                auto bytes = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(file)));
                Csm::CubismMotion* motion = Csm::CubismMotion::Create(
                        bytes.data(), static_cast<Csm::csmSizeInt>(bytes.size()));
                if (motion) tapBodyMotions_.PushBack(motion);
            } catch (const std::exception& error) {
                OutputDebugStringA((std::string("YuanLive2D tap body motion load skipped: ") + error.what() + "\n").c_str());
            }
        }

        if (setting_->GetPhysicsFileName()[0]) {
            auto physics = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(setting_->GetPhysicsFileName())));
            LoadPhysics(physics.data(), static_cast<Csm::csmSizeInt>(physics.size()));
            if (!_physics) throw std::runtime_error("Cubism failed to load physics");
        }

        if (_physics) {
            auto physicsJson = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(setting_->GetPhysicsFileName())));
            Csm::CubismPhysicsJson parsed(physicsJson.data(), static_cast<Csm::csmSizeInt>(physicsJson.size()));
            for (Csm::csmInt32 subRig = 0; subRig < parsed.GetSubRigCount(); ++subRig) {
                const Csm::csmInt32 outputCount = parsed.GetOutputCount(subRig);
                for (Csm::csmInt32 output = 0; output < outputCount; ++output) {
                    const Csm::CubismIdHandle destination = parsed.GetOutputsDestinationId(subRig, output);
                    bool alreadyTracked = false;
                    for (Csm::csmUint32 tracked = 0; tracked < physicsOutputIds_.GetSize(); ++tracked)
                        if (physicsOutputIds_[tracked] == destination) { alreadyTracked = true; break; }
                    if (!alreadyTracked) physicsOutputIds_.PushBack(destination);
                }
            }
        }

        Csm::csmMap<Csm::csmString, Csm::csmFloat32> layout;
        if (setting_->GetLayoutMap(layout)) _modelMatrix->SetupFromLayout(layout);
        for (Csm::csmInt32 i = 0; i < setting_->GetEyeBlinkParameterCount(); ++i)
            eyeBlinkIds_.PushBack(setting_->GetEyeBlinkParameterId(i));
        if (eyeBlinkIds_.GetSize() != 0) _eyeBlink = Csm::CubismEyeBlink::Create(setting_.get());

        _breath = Csm::CubismBreath::Create();
        Csm::csmVector<Csm::CubismBreath::BreathParameterData> breath;
        auto* ids = Csm::CubismFramework::GetIdManager();
        breath.PushBack({ids->GetId("ParamAngleX"), 0.0f, 15.0f, 6.5345f, 0.5f});
        breath.PushBack({ids->GetId("ParamAngleY"), 0.0f, 8.0f, 3.5345f, 0.5f});
        breath.PushBack({ids->GetId("ParamAngleZ"), 0.0f, 10.0f, 5.5345f, 0.5f});
        breath.PushBack({ids->GetId("ParamBodyAngleX"), 0.0f, 4.0f, 15.5345f, 0.5f});
        breath.PushBack({ids->GetId("ParamBreath"), 0.5f, 0.5f, 3.2345f, 0.5f});
        _breath->SetParameters(breath);

        CreateRenderer(static_cast<Csm::csmUint32>(framebufferWidth), static_cast<Csm::csmUint32>(framebufferHeight));
        if (!GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>()) throw std::runtime_error("Cubism renderer creation failed");
        YuanLive2DCheckGl("renderer creation");
        LoadTextures();
        _model->SaveParameters();
    } catch (...) {
        ReleaseTextures();
        ReleaseMotions();
        throw;
    }
}

YuanLive2DModel::~YuanLive2DModel() {
    ReleaseOutputTarget();
    ReleaseMotions();
    ReleaseTextures();
}

void YuanLive2DModel::Close() {
    if (closed_) return;
    ReleaseMotions();
    ReleaseOutputTarget();
    if (!textures_.empty()) {
        glDeleteTextures(static_cast<GLsizei>(textures_.size()), textures_.data());
        textures_.clear();
        YuanLive2DCheckGl("model texture destruction");
    }
    DeleteRenderer();
    YuanLive2DCheckGl("model renderer destruction");
    closed_ = true;
}

void YuanLive2DModel::ReleaseTextures() noexcept {
    if (!textures_.empty()) glDeleteTextures(static_cast<GLsizei>(textures_.size()), textures_.data());
    textures_.clear();
}

void YuanLive2DModel::ReleaseMotions() noexcept {
    if (_motionManager) _motionManager->StopAllMotions();
    if (_expressionManager) _expressionManager->StopAllMotions();
    for (Csm::csmUint32 i = 0; i < idleMotions_.GetSize(); ++i)
        Csm::ACubismMotion::Delete(idleMotions_[i]);
    idleMotions_.Clear();
    for (Csm::csmUint32 i = 0; i < tapBodyMotions_.GetSize(); ++i)
        Csm::ACubismMotion::Delete(tapBodyMotions_[i]);
    tapBodyMotions_.Clear();
    for (Csm::csmUint32 i = 0; i < expressions_.GetSize(); ++i)
        Csm::ACubismMotion::Delete(expressions_[i]);
    expressions_.Clear();
}

void YuanLive2DModel::EnsureOutputTarget(GLsizei width, GLsizei height) {
    if (outputTexture_ && outputFramebuffer_ && outputWidth_ == width && outputHeight_ == height) return;

    GLuint texture = 0;
    GLuint framebuffer = 0;
    glGenTextures(1, &texture);
    if (!texture) throw std::runtime_error("OpenGL output texture allocation failed");
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &framebuffer);
    if (!framebuffer) {
        glDeleteTextures(1, &texture);
        throw std::runtime_error("OpenGL output framebuffer allocation failed");
    }
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        glDeleteFramebuffers(1, &framebuffer);
        glDeleteTextures(1, &texture);
        std::ostringstream message;
        message << "Live2D output framebuffer incomplete: 0x" << std::hex << status;
        throw std::runtime_error(message.str());
    }

    ReleaseOutputTarget();
    outputTexture_ = texture;
    outputFramebuffer_ = framebuffer;
    outputWidth_ = width;
    outputHeight_ = height;
}

void YuanLive2DModel::ReleaseOutputTarget() noexcept {
    if (outputFramebuffer_) glDeleteFramebuffers(1, &outputFramebuffer_);
    if (outputTexture_) glDeleteTextures(1, &outputTexture_);
    outputFramebuffer_ = 0;
    outputTexture_ = 0;
    outputWidth_ = 0;
    outputHeight_ = 0;
}

void YuanLive2DModel::LoadTextures() {
    auto* renderer = GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>();
    std::uint64_t estimatedBytes = estimateTextureBytes(*setting_, modelHome_);
    if (estimatedBytes > textureMemoryBudgetBytes_) throw std::runtime_error("Estimated texture memory exceeds configured budget");
    if (estimatedBytes > (256ULL << 20)) {
        std::ostringstream warning;
        warning << "Live2D texture estimate warning: " << (estimatedBytes >> 20) << " MiB\n";
        OutputDebugStringA(warning.str().c_str());
    }
    textures_.reserve(static_cast<size_t>(setting_->GetTextureCount()));
    for (Csm::csmInt32 i = 0; i < setting_->GetTextureCount(); ++i) {
        // ponytail: synchronous first-load; move decode/upload off-thread only if profiling shows unacceptable load stalls.
        const char* name = setting_->GetTextureFileName(i);
        auto encoded = YuanLive2DReadBytes(YuanLive2DResolveContained(modelHome_, fs::u8path(name)));
        if (encoded.size() > INT_MAX) throw std::runtime_error("PNG file is too large to decode");
        int width = 0, height = 0, channels = 0;
        std::unique_ptr<stbi_uc, decltype(&stbi_image_free)> pixels(
                stbi_load_from_memory(encoded.data(), static_cast<int>(encoded.size()), &width, &height, &channels, 4),
                stbi_image_free);
        if (!pixels) throw std::runtime_error(std::string("PNG decode failed: ") + stbi_failure_reason());
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192)
            throw std::runtime_error("PNG dimensions must be within 8192 x 8192");
        const size_t byteCount = static_cast<size_t>(width) * height * 4;
        for (size_t p = 0; p < byteCount; p += 4) {
            pixels.get()[p] = static_cast<stbi_uc>(pixels.get()[p] * pixels.get()[p + 3] / 255);
            pixels.get()[p + 1] = static_cast<stbi_uc>(pixels.get()[p + 1] * pixels.get()[p + 3] / 255);
            pixels.get()[p + 2] = static_cast<stbi_uc>(pixels.get()[p + 2] * pixels.get()[p + 3] / 255);
        }
        YuanLive2DCheckGl("before texture upload");
        TextureOwner texture;
        glGenTextures(1, &texture.id);
        if (!texture.id) throw std::runtime_error("OpenGL texture allocation failed");
        glBindTexture(GL_TEXTURE_2D, texture.id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels.get());
        glGenerateMipmap(GL_TEXTURE_2D);
        YuanLive2DCheckGl("texture upload");
        textures_.push_back(texture.id);
        texture.id = 0;
        renderer->BindTexture(i, textures_.back());
    }
    renderer->IsPremultipliedAlpha(true);
}

std::uint64_t YuanLive2DEstimateTextureBytes(const std::filesystem::path& modelHome,
                                             const std::filesystem::path& manifest) {
    auto json = YuanLive2DReadBytes(YuanLive2DCanonicalContained(modelHome, manifest));
    Csm::CubismModelSettingJson setting(json.data(), static_cast<Csm::csmSizeInt>(json.size()));
    if (!setting.IsValid()) throw std::runtime_error("Manifest JSON parse failed");
    if (!setting.GetModelFileName()[0]) throw std::runtime_error("Manifest has no Moc file");
    return estimateTextureBytes(setting, modelHome);
}

void YuanLive2DModel::Render(float deltaSeconds, int framebufferWidth, int framebufferHeight,
                             float x, float y, float scale, float opacity) {
    Update(deltaSeconds);
    Draw(framebufferWidth, framebufferHeight, x, y, scale, opacity);
}

void YuanLive2DModel::Update(float deltaSeconds) {
    ValidateUpdate(deltaSeconds);
    deltaSeconds = std::clamp(deltaSeconds, 0.0f, 0.1f);
    elapsedSeconds_ += deltaSeconds;
    _model->LoadParameters();
    if (_eyeBlink) _eyeBlink->UpdateParameters(_model, deltaSeconds);
    _model->SaveParameters();
    if (_breath) _breath->UpdateParameters(_model, deltaSeconds);
    if (pendingControlFlags_ & 1) {
        if (idleMotions_.GetSize() > 0) {
            const Csm::csmInt32 index = static_cast<Csm::csmInt32>(std::rand()) % idleMotions_.GetSize();
            _motionManager->StartMotionPriority(idleMotions_[index], false, 2);
        }
        pendingControlFlags_ &= ~1;
    }
    if (pendingControlFlags_ & 2) {
        if (tapBodyMotions_.GetSize() > 0) {
            const Csm::csmInt32 index = static_cast<Csm::csmInt32>(std::rand()) % tapBodyMotions_.GetSize();
            _motionManager->StartMotionPriority(tapBodyMotions_[index], false, 3);
        } else {
            clickReactionTicks_ = 9;
        }
        pendingControlFlags_ &= ~2;
    }
    if (pendingControlFlags_ & 4) {
        if (expressions_.GetSize() > 0) {
            const Csm::csmInt32 index = static_cast<Csm::csmInt32>(std::rand()) % expressions_.GetSize();
            _expressionManager->StartMotion(expressions_[index], false);
        }
        pendingControlFlags_ &= ~4;
    }
    if (idleMotions_.GetSize() > 0) _motionManager->UpdateMotion(_model, deltaSeconds);
    else UpdateProgrammaticMotion(deltaSeconds);
    if (expressions_.GetSize() > 0) _expressionManager->UpdateMotion(_model, deltaSeconds);
    else UpdateProgrammaticExpression(deltaSeconds);
    ApplyGaze();
    if (idleMotions_.GetSize() == 0) UpdateProgrammaticSway();
    UpdateClickReaction();
    // Gaze must be written before physics so model rigs that derive head rotation from
    // ParamAngleX/Y through physics3 (for example ParamAngleX -> ParamAngle_HeadX) react
    // in the same frame instead of one frame late.
    if (_physics) _physics->Evaluate(_model, deltaSeconds);
    if (_physics && physicsOutputIds_.GetSize() != 0) {
        const Csm::csmInt32 parameterCount = _model->GetParameterCount();
        for (Csm::csmUint32 i = 0; i < physicsOutputIds_.GetSize(); ++i) {
            const Csm::csmInt32 index = _model->GetParameterIndex(physicsOutputIds_[i]);
            if (index < 0 || index >= parameterCount) continue;
            _model->SetParameterValue(index, _model->GetParameterValue(index) * physicsAmplitude_);
        }
    }
    _model->Update();
}

void YuanLive2DModel::Control(float gazeX, float gazeY, float physicsAmplitude, int flags) {
    gazeX_ = std::clamp(gazeX, -1.0f, 1.0f);
    gazeY_ = std::clamp(gazeY, -1.0f, 1.0f);
    physicsAmplitude_ = std::clamp(physicsAmplitude, 0.0f, 2.0f);
    pendingControlFlags_ |= flags;
}

void YuanLive2DModel::ApplyGaze() {
    const Csm::csmInt32 parameterCount = _model->GetParameterCount();
    auto apply = [this, parameterCount](const char* id, float look, float amplitudeScale) {
        const Csm::csmInt32 index = _model->GetParameterIndex(
                Csm::CubismFramework::GetIdManager()->GetId(id));
        if (index < 0 || index >= parameterCount) return;
        const float min = _model->GetParameterMinimumValue(index);
        const float max = _model->GetParameterMaximumValue(index);
        const float normalized = look * amplitudeScale;
        _model->SetParameterValue(index, min + (normalized + 1.0f) * 0.5f * (max - min));
    };
    apply("ParamAngleX", gazeX_, 1.0f);
    apply("ParamAngleY", gazeY_, 1.0f);
    apply("ParamBodyAngleX", gazeX_, 0.5f);
    apply("ParamBodyAngleY", gazeY_, 0.5f);
    apply("ParamEyeBallX", gazeX_, 0.8f);
    apply("ParamEyeBallY", gazeY_, 0.8f);
}

void YuanLive2DModel::UpdateProgrammaticSway() {
    auto* ids = Csm::CubismFramework::GetIdManager();
    const Csm::csmInt32 parameterCount = _model->GetParameterCount();
    const Csm::csmInt32 angleX = _model->GetParameterIndex(ids->GetId("ParamAngleX"));
    const Csm::csmInt32 angleZ = _model->GetParameterIndex(ids->GetId("ParamAngleZ"));
    // Add sway on top of the gaze value written by ApplyGaze instead of replacing it.
    if (angleX >= 0 && angleX < parameterCount)
        _model->SetParameterValue(angleX, _model->GetParameterValue(angleX) + swayX_);
    if (angleZ >= 0 && angleZ < parameterCount)
        _model->SetParameterValue(angleZ, _model->GetParameterValue(angleZ) + swayZ_);
}

void YuanLive2DModel::UpdateClickReaction() {
    if (clickReactionTicks_ <= 0) return;
    --clickReactionTicks_;
    auto* ids = Csm::CubismFramework::GetIdManager();
    const Csm::csmInt32 parameterCount = _model->GetParameterCount();
    const Csm::csmInt32 eyeL = _model->GetParameterIndex(ids->GetId("ParamEyeLOpen"));
    const Csm::csmInt32 eyeR = _model->GetParameterIndex(ids->GetId("ParamEyeROpen"));
    const Csm::csmInt32 angleZ = _model->GetParameterIndex(ids->GetId("ParamAngleZ"));
    if (eyeL >= 0 && eyeL < parameterCount) _model->SetParameterValue(eyeL, 0.1f);
    if (eyeR >= 0 && eyeR < parameterCount) _model->SetParameterValue(eyeR, 0.1f);
    if (angleZ >= 0 && angleZ < parameterCount)
        _model->SetParameterValue(angleZ, _model->GetParameterValue(angleZ) + 3.0f);
}

void YuanLive2DModel::UpdateProgrammaticMotion(Csm::csmFloat32 deltaSeconds) {
    blinkRandomSeconds_ -= deltaSeconds;
    if (blinkRandomSeconds_ <= 0.0f) {
        if (_eyeBlink)
            _eyeBlink->SetBlinkingInterval(2.0f + static_cast<Csm::csmFloat32>(std::rand() % 500) / 100.0f);
        blinkRandomSeconds_ = 3.0f + static_cast<Csm::csmFloat32>(std::rand() % 400) / 100.0f;
    }
    swayTimer_ -= deltaSeconds;
    if (swayTimer_ <= 0.0f) {
        swayTargetX_ = (static_cast<Csm::csmFloat32>(std::rand() % 2401) - 1200.0f) / 200.0f;
        swayTargetZ_ = (static_cast<Csm::csmFloat32>(std::rand() % 2401) - 1200.0f) / 200.0f;
        swayTimer_ = 3.0f + static_cast<Csm::csmFloat32>(std::rand() % 400) / 100.0f;
    }
    const Csm::csmFloat32 swayBlend = 1.0f - std::exp(-2.0f * deltaSeconds);
    swayX_ += (swayTargetX_ - swayX_) * swayBlend;
    swayZ_ += (swayTargetZ_ - swayZ_) * swayBlend;

    auto* ids = Csm::CubismFramework::GetIdManager();
    const Csm::csmInt32 parameterCount = _model->GetParameterCount();
    const Csm::csmInt32 breath = _model->GetParameterIndex(ids->GetId("ParamBreath"));

    breathTimer_ -= deltaSeconds;
    if (breathTimer_ <= 0.0f) {
        breathTargetScale_ = 0.85f + static_cast<Csm::csmFloat32>(std::rand() % 300) / 1000.0f;
        breathTimer_ = 4.0f + static_cast<Csm::csmFloat32>(std::rand() % 300) / 100.0f;
    }
    breathScale_ += (breathTargetScale_ - breathScale_) * (1.0f - std::exp(-1.5f * deltaSeconds));
    if (breath >= 0 && breath < parameterCount)
        _model->SetParameterValue(breath, _model->GetParameterValue(breath) * breathScale_);
}

void YuanLive2DModel::UpdateProgrammaticExpression(Csm::csmFloat32 deltaSeconds) {
    expressionTimer_ -= deltaSeconds;
    if (expressionTimer_ <= 0.0f) {
        const bool returnToCenter = (std::rand() % 3) == 0;
        browLYTarget_ = returnToCenter ? 0.0f : (static_cast<Csm::csmFloat32>(std::rand() % 401) - 200.0f) / 100.0f;
        browRYTarget_ = returnToCenter ? 0.0f : (static_cast<Csm::csmFloat32>(std::rand() % 401) - 200.0f) / 100.0f;
        mouthOpenYTarget_ = returnToCenter ? 0.0f : static_cast<Csm::csmFloat32>(std::rand() % 81) / 100.0f;
        expressionTimer_ = 1.5f + static_cast<Csm::csmFloat32>(std::rand() % 300) / 100.0f;
    }
    const Csm::csmFloat32 blend = 1.0f - std::exp(-2.5f * deltaSeconds);
    browLY_ += (browLYTarget_ - browLY_) * blend;
    browRY_ += (browRYTarget_ - browRY_) * blend;
    mouthOpenY_ += (mouthOpenYTarget_ - mouthOpenY_) * blend;

    auto* ids = Csm::CubismFramework::GetIdManager();
    const Csm::csmInt32 parameterCount = _model->GetParameterCount();
    const Csm::csmInt32 browL = _model->GetParameterIndex(ids->GetId("ParamBrowLY"));
    const Csm::csmInt32 browR = _model->GetParameterIndex(ids->GetId("ParamBrowRY"));
    const Csm::csmInt32 mouth = _model->GetParameterIndex(ids->GetId("ParamMouthOpenY"));
    if (browL >= 0 && browL < parameterCount) _model->SetParameterValue(browL, browLY_);
    if (browR >= 0 && browR < parameterCount) _model->SetParameterValue(browR, browRY_);
    if (mouth >= 0 && mouth < parameterCount) _model->SetParameterValue(mouth, mouthOpenY_);
}

YuanLive2DModel::TextureFrame YuanLive2DModel::Draw(
        int framebufferWidth, int framebufferHeight,
        float x, float y, float scale, float opacity) {
    ValidateDraw(framebufferWidth, framebufferHeight, x, y, scale, opacity);
    EnsureOutputTarget(framebufferWidth, framebufferHeight);
    opacity = std::clamp(opacity, 0.0f, 1.0f);

    glBindFramebuffer(GL_FRAMEBUFFER, outputFramebuffer_);
    glViewport(0, 0, outputWidth_, outputHeight_);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    Csm::CubismMatrix44 matrix = drawMatrix(framebufferWidth, framebufferHeight, x, y, scale, _modelMatrix);
    auto* renderer = GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>();
    renderer->SetRenderTargetSize(framebufferWidth, framebufferHeight);
    renderer->SetMvpMatrix(&matrix);
    renderer->SetModelColor(1.0f, 1.0f, 1.0f, opacity * _model->GetModelOpacity());
    renderer->DrawModel();
    YuanLive2DCheckGl("Cubism offscreen draw");

    // ponytail: Cubism's renderer leaves GL state (framebuffer binding, viewport, scissor)
    // in whatever shape it last used. Re-establish ours before clearing, otherwise glClear
    // hits the default framebuffer or the wrong region and the dark area keeps flickering.
    glBindFramebuffer(GL_FRAMEBUFFER, outputFramebuffer_);
    glViewport(0, 0, outputWidth_, outputHeight_);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);

    // ponytail: Clear FBO outside the model's bounding box so the mask/clamp subprocess
    // cannot leave semi-transparent pixels scattered across the rest of the texture.
    // Without this, dragging the full-screen quad across the GUI blends those pixels
    // every frame and the surrounding GUI elements flicker.
    auto bbox = Bounds(framebufferWidth, framebufferHeight, x, y, scale);
    const int bL = (int)(std::max)(0.0f, bbox[0]);
    const int bR = (int)(std::min)((float)outputWidth_, bbox[2]);
    const int bT = (int)(std::max)(0.0f, bbox[1]);
    const int bB = (int)(std::min)((float)outputHeight_, bbox[3]);
    if (bL > 0 || bT > 0 || bR < outputWidth_ || bB < outputHeight_) {
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // bbox Y is screen-space (top-down); GL scissor uses bottom-up.
        if (bT > 0) { glScissor(0, outputHeight_ - bT, outputWidth_, bT); glEnable(GL_SCISSOR_TEST); glClear(GL_COLOR_BUFFER_BIT); }
        if (outputHeight_ > bB) { glScissor(0, 0, outputWidth_, outputHeight_ - bB); glEnable(GL_SCISSOR_TEST); glClear(GL_COLOR_BUFFER_BIT); }
        if (bL > 0) { glScissor(0, outputHeight_ - bB, bL, bB - bT); glEnable(GL_SCISSOR_TEST); glClear(GL_COLOR_BUFFER_BIT); }
        if (outputWidth_ > bR) { glScissor(bR, outputHeight_ - bB, outputWidth_ - bR, bB - bT); glEnable(GL_SCISSOR_TEST); glClear(GL_COLOR_BUFFER_BIT); }
        glDisable(GL_SCISSOR_TEST);
    }
    return {outputTexture_, outputWidth_, outputHeight_};
}

std::array<float, 4> YuanLive2DModel::Bounds(int framebufferWidth, int framebufferHeight,
                                             float x, float y, float scale) const {
    ValidateDraw(framebufferWidth, framebufferHeight, x, y, scale, 1.0f);
    auto matrix = drawMatrix(framebufferWidth, framebufferHeight, x, y, scale, _modelMatrix);
    const float infinity = std::numeric_limits<float>::infinity();
    std::array<float, 4> result{infinity, infinity, -infinity, -infinity};
    const Csm::csmInt32 drawableCount = _model->GetDrawableCount();
    for (Csm::csmInt32 drawable = 0; drawable < drawableCount; ++drawable) {
        const auto* positions = _model->GetDrawableVertexPositions(drawable);
        const Csm::csmInt32 vertexCount = _model->GetDrawableVertexCount(drawable);
        for (Csm::csmInt32 vertex = 0; vertex < vertexCount; ++vertex) {
            const float pixelX = (matrix.TransformX(positions[vertex].X) + 1.0f) * 0.5f * framebufferWidth;
            const float pixelY = (1.0f - matrix.TransformY(positions[vertex].Y)) * 0.5f * framebufferHeight;
            result[0] = (std::min)(result[0], pixelX);
            result[1] = (std::min)(result[1], pixelY);
            result[2] = (std::max)(result[2], pixelX);
            result[3] = (std::max)(result[3], pixelY);
        }
    }
    if (result[0] <= result[2] && result[1] <= result[3]) return result;

    Live2D::Cubism::Core::csmVector2 sizePixels{}, originPixels{};
    float pixelsPerUnit = 0.0f;
    Live2D::Cubism::Core::csmReadCanvasInfo(
            _model->GetModel(), &sizePixels, &originPixels, &pixelsPerUnit);
    if (!std::isfinite(pixelsPerUnit) || !(pixelsPerUnit > 0.0f))
        throw std::runtime_error("Invalid Live2D canvas scale");
    const float left = -originPixels.X / pixelsPerUnit;
    const float right = (sizePixels.X - originPixels.X) / pixelsPerUnit;
    const float bottom = -originPixels.Y / pixelsPerUnit;
    const float top = (sizePixels.Y - originPixels.Y) / pixelsPerUnit;
    const std::array<float, 4> xs{left, right, left, right};
    const std::array<float, 4> ys{bottom, bottom, top, top};
    for (size_t i = 0; i < xs.size(); ++i) {
        const float pixelX = (matrix.TransformX(xs[i]) + 1.0f) * 0.5f * framebufferWidth;
        const float pixelY = (1.0f - matrix.TransformY(ys[i])) * 0.5f * framebufferHeight;
        result[0] = (std::min)(result[0], pixelX);
        result[1] = (std::min)(result[1], pixelY);
        result[2] = (std::max)(result[2], pixelX);
        result[3] = (std::max)(result[3], pixelY);
    }
    return result;
}

void YuanLive2DModel::ValidateUpdate(float deltaSeconds) const {
    if (!_model || !std::isfinite(deltaSeconds)) throw std::invalid_argument("Invalid update arguments");
}

void YuanLive2DModel::ValidateDraw(int framebufferWidth, int framebufferHeight, float x, float y, float scale,
                                   float opacity) const {
    if (!_model || framebufferWidth <= 0 || framebufferHeight <= 0
            || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(scale) || !std::isfinite(opacity)
            || !(scale > 0.0f)) throw std::invalid_argument("Invalid draw arguments");
}
