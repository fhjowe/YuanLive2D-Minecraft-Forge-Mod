#pragma once

#include <GL/glew.h>
#include <array>

void YuanLive2DCheckGl(const char* operation);
void YuanLive2DBeginGlOperation(const char* operation) noexcept;
void YuanLive2DValidateGlFunctions();

class YuanLive2DGlState {
public:
    YuanLive2DGlState();
    ~YuanLive2DGlState() noexcept;

    YuanLive2DGlState(const YuanLive2DGlState&) = delete;
    YuanLive2DGlState& operator=(const YuanLive2DGlState&) = delete;

private:
    PFNGLACTIVETEXTUREPROC activeTexture_;
    PFNGLBINDFRAMEBUFFERPROC bindFramebuffer_;
    PFNGLBINDVERTEXARRAYPROC bindVertexArray_;
    PFNGLBINDBUFFERPROC bindBuffer_;
    PFNGLUSEPROGRAMPROC useProgram_;
    PFNGLBLENDFUNCSEPARATEPROC blendFuncSeparate_;
    PFNGLBLENDEQUATIONSEPARATEPROC blendEquationSeparate_;
    GLint drawFramebuffer_ = 0, readFramebuffer_ = 0, program_ = 0, activeTextureUnit_ = GL_TEXTURE0;
    GLint vertexArray_ = 0, arrayBuffer_ = 0, elementBuffer_ = 0;
    GLint viewport_[4]{}, scissorBox_[4]{};
    GLint blendSrcRgb_ = 0, blendDstRgb_ = 0, blendSrcAlpha_ = 0, blendDstAlpha_ = 0;
    GLint blendEquationRgb_ = 0, blendEquationAlpha_ = 0, depthFunc_ = 0, cullMode_ = 0, frontFace_ = 0;
    GLfloat clearColor_[4]{};
    GLboolean scissor_ = GL_FALSE, blend_ = GL_FALSE, depth_ = GL_FALSE, depthMask_ = GL_FALSE;
    GLboolean cull_ = GL_FALSE, stencil_ = GL_FALSE, colorLogicOp_ = GL_FALSE, colorMask_[4]{};
    std::array<GLenum, 4> textureUnits_{};
    std::array<GLint, 4> textures_{};
    int textureUnitCount_ = 0;
};
