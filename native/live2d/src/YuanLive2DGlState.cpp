#include "YuanLive2DGlState.hpp"

#include <stdexcept>
#include <sstream>
#include <windows.h>

namespace {
template<class T>
T glFunction(const char* name) {
    auto address = wglGetProcAddress(name);
    if (!address) address = GetProcAddress(GetModuleHandleW(L"opengl32.dll"), name);
    if (!address || address == reinterpret_cast<PROC>(1) || address == reinterpret_cast<PROC>(2)
            || address == reinterpret_cast<PROC>(3) || address == reinterpret_cast<PROC>(-1))
        throw std::runtime_error(std::string("OpenGL function unavailable: ") + name);
    return reinterpret_cast<T>(address);
}

void setEnabled(GLenum state, GLboolean enabled) {
    enabled ? glEnable(state) : glDisable(state);
}
}

void YuanLive2DCheckGl(const char* operation) {
    GLenum first = glGetError();
    if (first == GL_NO_ERROR) return;
    while (glGetError() != GL_NO_ERROR) {}
    std::ostringstream message;
    message << operation << " OpenGL error 0x" << std::hex << first;
    throw std::runtime_error(message.str());
}

void YuanLive2DBeginGlOperation(const char* operation) noexcept {
    GLenum first = glGetError();
    if (first == GL_NO_ERROR) return;
    while (glGetError() != GL_NO_ERROR) {}
    std::ostringstream message;
    message << "Live2D ignored pre-existing host OpenGL error before " << operation << ": 0x" << std::hex << first << '\n';
    OutputDebugStringA(message.str().c_str());
}

void YuanLive2DValidateGlFunctions() {
    // Mirrors the unconditional CSM_TARGET_WIN_GL table in CubismRenderer_OpenGLES2.cpp.
    for (const char* name : {
            "glActiveTexture", "glBindBuffer", "glGenBuffers", "glDeleteBuffers", "glBufferData", "glDrawElements", "glUseProgram", "glUniform1i", "glGetAttribLocation",
            "glGetUniformLocation", "glBlendFuncSeparate", "glEnableVertexAttribArray",
            "glDisableVertexAttribArray", "glVertexAttribPointer", "glUniformMatrix4fv", "glUniform1f",
            "glUniform4f", "glLinkProgram", "glGetProgramiv", "glValidateProgram", "glGetProgramInfoLog",
            "glCreateProgram", "glDeleteProgram", "glShaderSource", "glGetShaderiv", "glCompileShader",
            "glCreateShader", "glAttachShader", "glDetachShader", "glDeleteShader", "glGenFramebuffers",
            "glGenRenderbuffers", "glBindFramebuffer", "glBlitFramebuffer", "glBindRenderbuffer",
            "glDeleteRenderbuffers", "glDeleteFramebuffers", "glFramebufferTexture2D",
            "glRenderbufferStorage", "glFramebufferRenderbuffer", "glCheckFramebufferStatus",
            "glGetVertexAttribiv",
            // Integration-owned operations and state restoration.
            "glGenerateMipmap", "glGenVertexArrays", "glBindVertexArray", "glDeleteVertexArrays",
            "glBlendEquationSeparate"})
        glFunction<PROC>(name);
#if defined(GLEW_ARB_texture_barrier)
    if (GLEW_ARB_texture_barrier) glFunction<PROC>("glTextureBarrier");
#elif defined(GLEW_NV_texture_barrier)
    if (GLEW_NV_texture_barrier) glFunction<PROC>("glTextureBarrier");
#endif
}

YuanLive2DGlState::YuanLive2DGlState()
    : activeTexture_(glFunction<PFNGLACTIVETEXTUREPROC>("glActiveTexture")),
      bindFramebuffer_(glFunction<PFNGLBINDFRAMEBUFFERPROC>("glBindFramebuffer")),
      bindVertexArray_(glFunction<PFNGLBINDVERTEXARRAYPROC>("glBindVertexArray")),
      bindBuffer_(glFunction<PFNGLBINDBUFFERPROC>("glBindBuffer")),
      useProgram_(glFunction<PFNGLUSEPROGRAMPROC>("glUseProgram")),
      blendFuncSeparate_(glFunction<PFNGLBLENDFUNCSEPARATEPROC>("glBlendFuncSeparate")),
      blendEquationSeparate_(glFunction<PFNGLBLENDEQUATIONSEPARATEPROC>("glBlendEquationSeparate")) {
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawFramebuffer_);
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &readFramebuffer_);
    glGetIntegerv(GL_CURRENT_PROGRAM, &program_);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTextureUnit_);
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vertexArray_);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &arrayBuffer_);
    glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, &elementBuffer_);
    glGetIntegerv(GL_VIEWPORT, viewport_);
    glGetIntegerv(GL_SCISSOR_BOX, scissorBox_);
    glGetIntegerv(GL_BLEND_SRC_RGB, &blendSrcRgb_);
    glGetIntegerv(GL_BLEND_DST_RGB, &blendDstRgb_);
    glGetIntegerv(GL_BLEND_SRC_ALPHA, &blendSrcAlpha_);
    glGetIntegerv(GL_BLEND_DST_ALPHA, &blendDstAlpha_);
    glGetIntegerv(GL_BLEND_EQUATION_RGB, &blendEquationRgb_);
    glGetIntegerv(GL_BLEND_EQUATION_ALPHA, &blendEquationAlpha_);
    glGetIntegerv(GL_DEPTH_FUNC, &depthFunc_);
    glGetIntegerv(GL_CULL_FACE_MODE, &cullMode_);
    glGetIntegerv(GL_FRONT_FACE, &frontFace_);
    glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor_);
    glGetBooleanv(GL_DEPTH_WRITEMASK, &depthMask_);
    glGetBooleanv(GL_COLOR_WRITEMASK, colorMask_);
    scissor_ = glIsEnabled(GL_SCISSOR_TEST);
    blend_ = glIsEnabled(GL_BLEND);
    depth_ = glIsEnabled(GL_DEPTH_TEST);
    cull_ = glIsEnabled(GL_CULL_FACE);
    stencil_ = glIsEnabled(GL_STENCIL_TEST);
    colorLogicOp_ = glIsEnabled(GL_COLOR_LOGIC_OP);
    const GLenum units[] = {GL_TEXTURE0, GL_TEXTURE1, GL_TEXTURE2, static_cast<GLenum>(activeTextureUnit_)};
    for (GLenum unit : units) {
        bool duplicate = false;
        for (int i = 0; i < textureUnitCount_; ++i) duplicate |= textureUnits_[i] == unit;
        if (duplicate) continue;
        activeTexture_(unit);
        textureUnits_[textureUnitCount_] = unit;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &textures_[textureUnitCount_++]);
    }
    activeTexture_(activeTextureUnit_);
}

YuanLive2DGlState::~YuanLive2DGlState() noexcept {
    bindFramebuffer_(GL_DRAW_FRAMEBUFFER, drawFramebuffer_);
    bindFramebuffer_(GL_READ_FRAMEBUFFER, readFramebuffer_);
    useProgram_(program_);
    for (int i = 0; i < textureUnitCount_; ++i) {
        activeTexture_(textureUnits_[i]);
        glBindTexture(GL_TEXTURE_2D, textures_[i]);
    }
    activeTexture_(activeTextureUnit_);
    bindVertexArray_(vertexArray_);
    bindBuffer_(GL_ELEMENT_ARRAY_BUFFER, elementBuffer_);
    glViewport(viewport_[0], viewport_[1], viewport_[2], viewport_[3]);
    glScissor(scissorBox_[0], scissorBox_[1], scissorBox_[2], scissorBox_[3]);
    setEnabled(GL_SCISSOR_TEST, scissor_);
    setEnabled(GL_BLEND, blend_);
    blendFuncSeparate_(blendSrcRgb_, blendDstRgb_, blendSrcAlpha_, blendDstAlpha_);
    blendEquationSeparate_(blendEquationRgb_, blendEquationAlpha_);
    setEnabled(GL_DEPTH_TEST, depth_);
    glDepthFunc(depthFunc_);
    glDepthMask(depthMask_);
    setEnabled(GL_CULL_FACE, cull_);
    glCullFace(cullMode_);
    glFrontFace(frontFace_);
    setEnabled(GL_STENCIL_TEST, stencil_);
    setEnabled(GL_COLOR_LOGIC_OP, colorLogicOp_);
    glColorMask(colorMask_[0], colorMask_[1], colorMask_[2], colorMask_[3]);
    glClearColor(clearColor_[0], clearColor_[1], clearColor_[2], clearColor_[3]);
    bindBuffer_(GL_ARRAY_BUFFER, arrayBuffer_);
}
