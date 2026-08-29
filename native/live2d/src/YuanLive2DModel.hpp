#pragma once

#include <Model/CubismUserModel.hpp>
#include <CubismModelSettingJson.hpp>
#include <Motion/CubismExpressionMotion.hpp>
#include <Motion/CubismMotion.hpp>
#include <GL/glew.h>
#include <array>
#include <filesystem>
#include <memory>
#include <vector>

class YuanLive2DModel final : public Csm::CubismUserModel {
public:
    struct TextureFrame { GLuint texture; GLsizei width; GLsizei height; };
    YuanLive2DModel(std::filesystem::path modelHome, std::filesystem::path manifest,
                   int framebufferWidth, int framebufferHeight, std::uint64_t textureMemoryBudgetBytes);
    ~YuanLive2DModel() override;

    void Render(float deltaSeconds, int framebufferWidth, int framebufferHeight,
                float x, float y, float scale, float opacity);
    void Update(float deltaSeconds);
    void Control(float gazeX, float gazeY, float physicsAmplitude, int flags);
    Csm::CubismMotionManager* GetMotionManager() { return _motionManager; }
    Csm::CubismExpressionMotionManager* GetExpressionManager() { return _expressionManager; }
    TextureFrame Draw(int framebufferWidth, int framebufferHeight,
                      float x, float y, float scale, float opacity);
    std::array<float, 4> Bounds(int framebufferWidth, int framebufferHeight,
                                float x, float y, float scale) const;
    void ValidateUpdate(float deltaSeconds) const;
    void ValidateDraw(int framebufferWidth, int framebufferHeight,
                      float x, float y, float scale, float opacity) const;
    void Close();
private:
    void LoadTextures();
    void ReleaseTextures() noexcept;
    void ReleaseMotions() noexcept;
    void EnsureOutputTarget(GLsizei width, GLsizei height);
    void ReleaseOutputTarget() noexcept;
    void ApplyGaze();
    void UpdateProgrammaticMotion(Csm::csmFloat32 deltaSeconds);
    void UpdateProgrammaticExpression(Csm::csmFloat32 deltaSeconds);
    void UpdateProgrammaticSway();
    void UpdateClickReaction();

    std::unique_ptr<Csm::CubismModelSettingJson> setting_;
    std::filesystem::path modelHome_;
    std::filesystem::path manifest_;
    Csm::csmVector<Csm::CubismIdHandle> eyeBlinkIds_;
    std::vector<GLuint> textures_;
    GLuint outputTexture_ = 0;
    GLuint outputFramebuffer_ = 0;
    GLsizei outputWidth_ = 0;
    GLsizei outputHeight_ = 0;
    float elapsedSeconds_ = 0.0f;
    float gazeX_ = 0.0f;
    float gazeY_ = 0.0f;
    float physicsAmplitude_ = 1.0f;
    int pendingControlFlags_ = 0;
    Csm::csmInt32 clickReactionTicks_ = 0;
    Csm::csmFloat32 blinkRandomSeconds_ = 0.0f;
    Csm::csmFloat32 swayTargetX_ = 0.0f;
    Csm::csmFloat32 swayTargetZ_ = 0.0f;
    Csm::csmFloat32 swayTimer_ = 0.0f;
    Csm::csmVector<Csm::CubismMotion*> idleMotions_;
    Csm::csmVector<Csm::CubismMotion*> tapBodyMotions_;
    Csm::csmVector<Csm::CubismExpressionMotion*> expressions_;
    Csm::csmFloat32 swayX_ = 0.0f;
    Csm::csmFloat32 swayZ_ = 0.0f;
    Csm::csmFloat32 breathScale_ = 1.0f;
    Csm::csmFloat32 breathTargetScale_ = 1.0f;
    Csm::csmFloat32 breathTimer_ = 0.0f;
    Csm::csmFloat32 expressionTimer_ = 0.0f;
    Csm::csmFloat32 browLY_ = 0.0f;
    Csm::csmFloat32 browRY_ = 0.0f;
    Csm::csmFloat32 mouthOpenY_ = 0.0f;
    Csm::csmFloat32 browLYTarget_ = 0.0f;
    Csm::csmFloat32 browRYTarget_ = 0.0f;
    Csm::csmFloat32 mouthOpenYTarget_ = 0.0f;
    Csm::csmVector<Csm::CubismIdHandle> physicsOutputIds_;
    bool closed_ = false;
    std::uint64_t textureMemoryBudgetBytes_;
};

void YuanLive2DSetShaderRoot(const std::filesystem::path& root);
void YuanLive2DClearShaderRoot();
Csm::csmByte* YuanLive2DLoadFile(const std::string path, Csm::csmSizeInt* size);
void YuanLive2DReleaseFile(Csm::csmByte* bytes);

std::uint64_t YuanLive2DEstimateTextureBytes(const std::filesystem::path& modelHome,
                                             const std::filesystem::path& manifest);
