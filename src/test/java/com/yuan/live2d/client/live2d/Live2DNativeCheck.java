package com.yuan.live2d.client.live2d;

import com.yuan.live2d.client.live2d.Live2DPaths;
import com.yuan.live2d.client.live2d.Live2DClientStateCheck;
import com.yuan.live2d.client.live2d.Live2DRuntimeCheck;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Live2DNativeCheck {
    public static void main(String[] args) throws Exception {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path cmake = project.resolve("native/live2d/CMakeLists.txt");
        Path bridge = project.resolve("native/live2d/src/YuanLive2DBridge.cpp");
        Path glState = project.resolve("native/live2d/src/YuanLive2DGlState.cpp");
        Path model = project.resolve("native/live2d/src/YuanLive2DModel.cpp");
        Path support = project.resolve("native/live2d/src/YuanLive2DNativeSupport.cpp");
        Path smoke = project.resolve("native/live2d/tests/YuanLive2DNativeSmoke.cpp");
        Path patch = project.resolve("native/live2d/patch-framework.ps1");
        Path hud = project.resolve("src/main/java/com/yuan/live2d/client/live2d/Live2DHudRenderer.java");
        Path compositor = project.resolve("src/main/java/com/yuan/live2d/client/live2d/Live2DTextureRenderer.java");
        Path nativeApi = project.resolve("src/main/java/com/yuan/live2d/client/live2d/Live2DNative.java");
        String installerText = Files.readString(project.resolve("src/test/java/com/yuan/live2d/client/live2d/Live2DInstallCheck.java"));
        String gradle = Files.readString(project.resolve("build.gradle"));
        String cmakeText = Files.readString(cmake);
        String bridgeText = Files.readString(bridge);
        String modelText = Files.readString(model);
        assert cmakeText.contains("Live2DCubismCore");
        assert cmakeText.contains("JNI::JNI");
        assert cmakeText.contains("set(GLEW_PATH \"${GLEW_ROOT}\")");
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_version");
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_create");
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_render");
        assert bridgeText.contains("JNIEXPORT jlongArray JNICALL\nJava_com_yuan_live2d_client_live2d_Live2DNative_render");
        assert !bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_renderPreview");
        assert bridgeText.contains("jboolean updateModel");
        assert bridgeText.contains("NewLongArray(5)") && bridgeText.contains("SetLongArrayRegion");
        assert bridgeText.contains("RenderFailedBeforeUpdate");
        assert bridgeText.contains("RenderDrawnWithoutUpdate");
        assert bridgeText.contains("RenderDrawnWithUpdate");
        assert bridgeText.contains("RenderFailedAfterUpdate");
        assert bridgeText.contains("updateModel");
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_destroy");
        assert bridgeText.contains("JNIEXPORT jboolean JNICALL\nJava_com_yuan_live2d_client_live2d_Live2DNative_destroy");
        assert bridgeText.contains("found->second->Close()");
        assert bridgeText.indexOf("found->second->Close()") < bridgeText.lastIndexOf("models.erase(found)")
                : "native ownership must survive failed model close";
        assert bridgeText.contains("YuanLive2DCommitCreate(model");
        assert bridgeText.indexOf("YuanLive2DCheckGl(\"create state restoration\")")
                < bridgeText.indexOf("models.insert(staged.extract(createdHandle))")
                : "create must finish post-create checks before publication";
        assert bridgeText.contains("YuanLive2DAdvanceFinalCleanup(cleanup");
        int deleteVao = bridgeText.indexOf("glDeleteVertexArrays(1, &value)");
        int checkDeletedVao = bridgeText.indexOf("YuanLive2DCheckGl(\"final VAO destruction\")", deleteVao);
        int clearVao = bridgeText.indexOf("cubismVao = 0", deleteVao);
        assert deleteVao >= 0 && checkDeletedVao > deleteVao && clearVao > checkDeletedVao
                : "VAO deletion GL check must complete before cleanup progress is committed";
        assert bridgeText.contains("cubismVao = 0;");
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_lastError");
        String nativeApiText = Files.readString(nativeApi);
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_estimate")
                : "native estimate JNI must exist";
        assert bridgeText.contains("startFramework(shaderRoot)")
                : "estimate must ensure Cubism Framework is initialized";
        int estimateJni = bridgeText.indexOf("Java_com_yuan_live2d_client_live2d_Live2DNative_estimate");
        int estimateStartFramework = bridgeText.indexOf("startFramework(shaderRoot)", estimateJni);
        int destroyJni = bridgeText.indexOf("Java_com_yuan_live2d_client_live2d_Live2DNative_destroy");
        assert estimateJni >= 0 && estimateStartFramework > estimateJni && estimateStartFramework < destroyJni
                : "estimate JNI must initialize Cubism Framework before estimating";
        assert modelText.contains("YuanLive2DEstimateTextureBytes")
                && modelText.contains("mipChainBytes")
                : "estimate must share the mip chain formula with LoadTextures";
        assert nativeApiText.contains("static native long estimate(")
                : "Java native estimate declaration must exist";
        assert nativeApiText.contains("static native void control(")
                : "Java native control declaration must exist";
        assert bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_control")
                : "native control JNI must exist";
        assert !bridgeText.contains("Java_com_yuan_live2d_client_live2d_Live2DNative_glStateSnapshot")
                : "temporary GL snapshot JNI must be removed";
        assert bridgeText.contains("return textureFrame(env, updated ? RenderFailedAfterUpdate : RenderFailedBeforeUpdate, nullptr)")
                : "failed renders must return status without stale texture metadata";
        assert nativeApiText.contains("static native long[] render(");
        assert nativeApiText.contains("record StructuredFrame(");
        assert !nativeApiText.contains("renderPreview");
        assert !nativeApiText.contains("glStateSnapshot");
        assert !nativeApiText.contains("int clipX") && !nativeApiText.contains("int clipY")
                && !nativeApiText.contains("int clipWidth") && !nativeApiText.contains("int clipHeight")
                : "native preview contract must not expose clipping parameters";
        assert !bridgeText.contains("requireCompatibilityProfile");
        assert bridgeText.contains("requireCoreProfileBuffers");
        String glStateText = Files.readString(glState);
        for (String state : new String[] {
                "GL_DRAW_FRAMEBUFFER_BINDING", "GL_READ_FRAMEBUFFER_BINDING", "GL_CURRENT_PROGRAM",
                "GL_ACTIVE_TEXTURE", "GL_TEXTURE_BINDING_2D", "GL_VERTEX_ARRAY_BINDING",
                "GL_ARRAY_BUFFER_BINDING", "GL_ELEMENT_ARRAY_BUFFER_BINDING", "GL_VIEWPORT",
                "GL_SCISSOR_BOX", "GL_SCISSOR_TEST", "GL_BLEND", "GL_BLEND_SRC_RGB",
                "GL_BLEND_DST_RGB", "GL_BLEND_SRC_ALPHA", "GL_BLEND_DST_ALPHA",
                "GL_BLEND_EQUATION_RGB", "GL_BLEND_EQUATION_ALPHA", "GL_DEPTH_TEST",
                "GL_DEPTH_FUNC", "GL_DEPTH_WRITEMASK", "GL_CULL_FACE", "GL_CULL_FACE_MODE",
                "GL_COLOR_WRITEMASK", "GL_COLOR_LOGIC_OP"
        }) assert glStateText.contains(state) : "missing GL state guard coverage: " + state;
        for (String pair : new String[] {
                "glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING|bindFramebuffer_(GL_DRAW_FRAMEBUFFER",
                "glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING|bindFramebuffer_(GL_READ_FRAMEBUFFER",
                "glGetIntegerv(GL_CURRENT_PROGRAM|useProgram_(program_)",
                "glGetIntegerv(GL_ACTIVE_TEXTURE|activeTexture_(activeTextureUnit_)",
                "glGetIntegerv(GL_VERTEX_ARRAY_BINDING|bindVertexArray_(vertexArray_)",
                "glGetIntegerv(GL_ARRAY_BUFFER_BINDING|bindBuffer_(GL_ARRAY_BUFFER, arrayBuffer_)",
                "glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING|bindBuffer_(GL_ELEMENT_ARRAY_BUFFER, elementBuffer_)",
                "glGetIntegerv(GL_VIEWPORT|glViewport(",
                "glGetIntegerv(GL_SCISSOR_BOX|glScissor(",
                "glGetBooleanv(GL_DEPTH_WRITEMASK|glDepthMask(depthMask_)",
                "glGetBooleanv(GL_COLOR_WRITEMASK|glColorMask("
        }) {
            String[] tokens = pair.split("\\|", -1);
            assert glStateText.contains(tokens[0]) && glStateText.contains(tokens[1])
                    : "GL state must be captured and restored: " + pair;
        }
        int restoreVao = glStateText.indexOf("bindVertexArray_(vertexArray_)");
        int restoreEbo = glStateText.indexOf("bindBuffer_(GL_ELEMENT_ARRAY_BUFFER, elementBuffer_)");
        assert restoreVao >= 0 && restoreEbo > restoreVao : "host VAO must be restored before its EBO";
        assert !glStateText.contains("vertexAttrib_[4]") : "fixed attribute snapshots corrupt host VAOs";
        assert bridgeText.contains("wglGetCurrentContext()");
        assert bridgeText.contains("HGLRC renderContext");
        assert bridgeText.contains("glGenVertexArrays");
        assert bridgeText.contains("glBindVertexArray(cubismVao)");
        assert bridgeText.contains("glDeleteVertexArrays");
        assert bridgeText.contains("requireOwner(currentContext())");
        assert bridgeText.contains("renderContext = context");
        assert bridgeText.contains("renderContext = nullptr");
        assert bridgeText.contains("glewExperimental = GL_TRUE");
        assert bridgeText.contains("GL_INVALID_ENUM");
        assert bridgeText.contains("GL_CONTEXT_PROFILE_MASK");
        assert bridgeText.contains("GL_CONTEXT_COMPATIBILITY_PROFILE_BIT");
        assert bridgeText.contains("GL_CONTEXT_CORE_PROFILE_BIT");
        assert bridgeText.contains("Cubism VBO/EBO rendering");
        assert bridgeText.contains("[E]");
        assert bridgeText.contains("cubismOperationActive");
        assert bridgeText.contains("class CubismOperation");
        assert bridgeText.contains("YuanLive2DBeginGlOperation(\"create\")");
        assert bridgeText.contains("YuanLive2DBeginGlOperation(\"render\")");
        assert bridgeText.contains("YuanLive2DBeginGlOperation(\"destroy\")");
        assert bridgeText.contains("YuanLive2DGlState operationState") : "create rollback and final cleanup need one outer state guard";
        assert bridgeText.contains("GetStringRegion");
        assert bridgeText.contains("ExceptionCheck");
        assert !bridgeText.contains("GetStringChars");
        assert modelText.contains("glGenerateMipmap(GL_TEXTURE_2D)");
        assert modelText.contains("stbi_info_from_memory") : "texture budget must be checked before decode";
        assert modelText.contains("textureMemoryBudgetBytes") : "native texture budget must be configurable";
        assert modelText.contains("ponytail: synchronous first-load") : "first-load stall ceiling must be documented";
        assert modelText.contains("YuanLive2DCheckGl");
        assert modelText.contains("values[5] = 2.0f * scale / framebufferHeight;")
                : "Cubism model Y axis is already upward; a negative scale renders it upside down";
        assert !modelText.contains("values[5] = -2.0f * scale / framebufferHeight;");
        int updateMethod = modelText.indexOf("void YuanLive2DModel::Update(float deltaSeconds)");
        int gazeCall = modelText.indexOf("ApplyGaze();", updateMethod);
        int motionUpdate = modelText.indexOf("_motionManager->UpdateMotion", updateMethod);
        int programmaticMotion = modelText.indexOf("UpdateProgrammaticMotion(deltaSeconds);", updateMethod);
        int expressionUpdate = modelText.indexOf("_expressionManager->UpdateMotion", updateMethod);
        int programmaticExpression = modelText.indexOf("UpdateProgrammaticExpression(deltaSeconds);", updateMethod);
        int modelUpdate = modelText.indexOf("_model->Update();", gazeCall);
        assert updateMethod >= 0 && gazeCall > motionUpdate && gazeCall > programmaticMotion
                && gazeCall > expressionUpdate && gazeCall > programmaticExpression && modelUpdate > gazeCall
                : "gaze must be applied after motion and expression updates, before model update";
        assert modelText.contains("ParamBodyAngleX")
                : "ApplyGaze must drive the body turn parameter";
        int programmaticSway = modelText.indexOf("UpdateProgrammaticSway();", updateMethod);
        assert modelText.contains("UpdateProgrammaticSway();") && programmaticSway > gazeCall
                && modelText.indexOf("UpdateClickReaction();", updateMethod) > programmaticSway
                : "programmatic sway must layer on gaze before the one-shot click reaction";
        assert modelText.contains("clickReactionTicks_ = 9")
                && modelText.contains("SetParameterValue(eyeL, 0.1f)")
                && modelText.contains("SetParameterValue(eyeR, 0.1f)")
                && modelText.contains("SetParameterValue(angleZ, _model->GetParameterValue(angleZ) + 3.0f)")
                && !modelText.contains("SetBlinkingInterval(0.15f)")
                : "click fallback must be a one-shot procedural squint plus head tilt";
        assert modelText.contains("textures_.reserve");
        assert modelText.contains("std::unique_ptr<stbi_uc") : "decoded pixels need scoped ownership";
        assert modelText.contains("void YuanLive2DModel::Close()") : "model needs explicit checked GL release";
        int ensureOutput = modelText.indexOf("void YuanLive2DModel::EnsureOutputTarget");
        int generateOutputTexture = modelText.indexOf("glGenTextures(1, &texture);", ensureOutput);
        int rejectOutputTexture = modelText.indexOf("if (!texture) throw std::runtime_error(\"OpenGL output texture allocation failed\");",
                generateOutputTexture);
        int bindOutputTexture = modelText.indexOf("glBindTexture(GL_TEXTURE_2D, texture);", generateOutputTexture);
        assert generateOutputTexture >= 0 && rejectOutputTexture > generateOutputTexture
                && bindOutputTexture > rejectOutputTexture
                : "output texture allocation must reject name 0 before binding";
        int generateOutputFramebuffer = modelText.indexOf("glGenFramebuffers(1, &framebuffer);", ensureOutput);
        int rejectOutputFramebuffer = modelText.indexOf("if (!framebuffer) {", generateOutputFramebuffer);
        int cleanupProvisionalTexture = modelText.indexOf("glDeleteTextures(1, &texture);", rejectOutputFramebuffer);
        int throwOutputFramebuffer = modelText.indexOf("throw std::runtime_error(\"OpenGL output framebuffer allocation failed\");",
                rejectOutputFramebuffer);
        int bindOutputFramebuffer = modelText.indexOf("glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);",
                generateOutputFramebuffer);
        assert generateOutputFramebuffer >= 0 && rejectOutputFramebuffer > generateOutputFramebuffer
                && cleanupProvisionalTexture > rejectOutputFramebuffer
                && throwOutputFramebuffer > cleanupProvisionalTexture
                && bindOutputFramebuffer > throwOutputFramebuffer
                : "output framebuffer allocation must reject name 0 and release the provisional texture before binding";
        String supportText = Files.readString(support);
        assert supportText.contains("CanonicalContained");
        assert supportText.contains("is_absolute()");
        assert supportText.contains("component == \"..\"");
        assert supportText.contains("numeric_limits<Csm::csmSizeInt>::max()");
        assert supportText.contains("numeric_limits<std::int64_t>::max()");
        assert modelText.contains("YuanLive2DCanonicalContained(modelHome_, manifest_)");
        assert modelText.contains("YuanLive2DResolveContained(modelHome_");
        assert cmakeText.contains("yuan_live2d_smoke");
        String smokeText = Files.readString(smoke);
        assert !smokeText.contains("assert(") : "native smoke checks must run in Release";
        assert smokeText.contains("requireThrows<std::invalid_argument>");
        assert smokeText.contains("requireThrows<std::runtime_error>");
        assert smokeText.contains("failed create published or lost local ownership");
        assert smokeText.contains("successful later stages repeated after Framework retry");
        assert smokeText.contains("model failure must retain dependent Framework, VAO, and check stages");
        assert smokeText.contains("wrong non-standard cleanup exception");
        assert smokeText.contains("cleanup did not preserve the first exception");
        assert smokeText.contains("failed VAO deletion check must retain retryable progress");
        String supportHeader = Files.readString(project.resolve("native/live2d/src/YuanLive2DNativeSupport.hpp"));
        assert supportHeader.contains("std::exception_ptr firstFailure")
                && supportHeader.contains("std::current_exception()")
                && supportHeader.contains("std::rethrow_exception(firstFailure)");
        for (String function : new String[] {
                "glActiveTexture", "glBindBuffer", "glUseProgram", "glUniform1i", "glGetAttribLocation",
                "glGetUniformLocation", "glBlendFuncSeparate", "glEnableVertexAttribArray",
                "glDisableVertexAttribArray", "glVertexAttribPointer", "glUniformMatrix4fv", "glUniform1f",
                "glUniform4f", "glLinkProgram", "glGetProgramiv", "glValidateProgram", "glGetProgramInfoLog",
                "glCreateProgram", "glDeleteProgram", "glShaderSource", "glGetShaderiv", "glCompileShader",
                "glCreateShader", "glAttachShader", "glDetachShader", "glDeleteShader", "glGenFramebuffers",
                "glGenRenderbuffers", "glBindFramebuffer", "glBlitFramebuffer", "glBindRenderbuffer",
                "glDeleteRenderbuffers", "glDeleteFramebuffers", "glFramebufferTexture2D",
                "glRenderbufferStorage", "glFramebufferRenderbuffer", "glCheckFramebufferStatus",
                 "glGetVertexAttribiv"
        }) assert glStateText.contains("\"" + function + "\"") : "missing SDK Windows GL function validation: " + function;
        for (String function : new String[] {"glGenBuffers", "glDeleteBuffers", "glBufferData", "glDrawElements"})
            assert glStateText.contains("\"" + function + "\"") : "missing core-profile buffer validation: " + function;
        assert glStateText.contains("GLEW_ARB_texture_barrier");
        assert glStateText.contains("GLEW_NV_texture_barrier");
        assert glStateText.contains("glTextureBarrier");
        assert gradle.contains("tasks.register('verifyCubismSdk', VerifySha256)");
        assert gradle.contains("tasks.register('downloadGlew', Download)");
        assert gradle.contains("tasks.register('downloadGlew', Download)")
                && gradle.substring(gradle.indexOf("tasks.register('downloadGlew', Download)"),
                gradle.indexOf("tasks.register('verifyGlew', VerifySha256)")).contains("outputs.upToDateWhen { false }")
                : "GLEW cache hash must be reevaluated every invocation";
        assert gradle.contains("tempHash") && gradle.indexOf("tempHash") < gradle.indexOf("Files.move(temp.toPath()")
                : "downloaded GLEW temp must be verified before atomic replacement";
        assert gradle.contains("tasks.register('verifyGlew', VerifySha256)");
        assert gradle.contains("tasks.register('extractGlew', Sync)");
        assert gradle.contains("def glewRoot = file(\"${cubismSdkParent}/glew-2.2.0\")");
        assert gradle.contains("tasks.register('extractCubismSdk', Sync)");
        assert gradle.contains("details.path = details.path.substring(details.path.indexOf('/') + 1)");
        assert gradle.contains("dependsOn verifyCubismSdk");
        assert gradle.contains("dependsOn verifyGlew");
        assert gradle.contains("A9046A913774395A095EDCC0B0AC2D81C3AACCA61787B39839B941E9BE14E0D4");
        assert gradle.contains("tasks.register('installLive2DRuntime', Sync)");
        assert gradle.contains("tasks.register('runLive2DNativeSmoke', Exec)");
        assert gradle.contains("dependsOn runLive2DNativeSmoke");
        assert gradle.contains("'-C', 'Release', '--output-on-failure'");
        assert gradle.contains("into 'THIRD_PARTY_NOTICES'");
        assert gradle.contains("rename { 'GLEW-LICENSE.txt' }");
        assert gradle.contains("inputs.dir file(\"${cubismSdkRoot}/Framework\")");
        assert gradle.contains("inputs.dir file(\"${glewRoot}/src\")");
        assert gradle.contains("inputs.dir file(\"${nativeSourceDir}/tests\")");
        assert gradle.contains("inputs.file file(\"${nativeSourceDir}/patch-framework.ps1\")");
        assert gradle.contains("jdk-17.0.19+10") && gradle.contains("${jdk17}/include");
        assert gradle.contains("options.release = 17");
        assert gradle.contains("verifyLive2DJarExclusions");
        assert gradle.contains("\"-DGLEW_ROOT=${glewRoot}\"");
        String patchText = Files.readString(patch);
        assert patchText.contains("YuanCoreProfilePatched");
        assert patchText.contains("glBufferData(GL_ARRAY_BUFFER");
        assert patchText.contains("glBufferData(GL_ELEMENT_ARRAY_BUFFER");
        assert patchText.contains("GetDrawableDynamicFlagVertexPositionsDidChange");
        assert patchText.contains("_drawableVertexBuffers[index]");
        assert patchText.contains("GL_STATIC_DRAW") : "UV/index buffers must upload once";
        assert patchText.contains("glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, nullptr)");
        assert patchText.contains("Expected exactly one SDK source match");
        assert !patchText.contains("SetIntegrationScissor")
                && !patchText.contains("ApplyIntegrationScissor")
                && !patchText.contains("_integrationRootFBO")
                : "host preview scissor integration must be removed";
        assert !modelText.contains("SetIntegrationScissor");
        assert patchText.contains("CSM_TARGET_WINGL");
        assert patchText.contains("Get-ChildItem -LiteralPath $openGlRoot -Filter '*.cpp'");
        assert patchText.contains("Get-BalancedCalls");
        assert patchText.contains("Split-CallArguments");
        assert patchText.contains("Unexpected glVertexAttribPointer pointer argument");
        assert patchText.contains("Buffer offset self-check rejected safe expression");
        assert patchText.contains("Buffer offset self-check accepted unsafe expression");
        Path generatedRenderer = project.resolve("build/live2d-native/cubism-sdk/Framework/src/Rendering/OpenGL/CubismRenderer_OpenGLES2.cpp");
        if (Files.exists(generatedRenderer)) {
            String generated = Files.readString(generatedRenderer);
            assert generated.contains("#ifdef CSM_TARGET_WIN_GL\n    s_isInitializeGlFunctionsSuccess = false");
            assert !generated.contains("CSM_TARGET_WINGL");
            assert generated.contains("offscreen->GetRenderTarget()->BeginDraw(oldFBO);")
                    && generated.contains("_currentFBO = offscreen->GetRenderTarget()->GetRenderTexture();\n    PreDraw();")
                    : "offscreen FBO entry must disable host scissor before clearing";
            assert generated.contains("_currentFBO = offscreen->GetRenderTarget()->GetOldFBO();\n    PreDraw();")
                    : "returning to parent/root FBO must re-disable scissor unconditionally";
            assert generated.contains("_modelRenderTargets[0].BeginDraw();\n    PreDraw();\n    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);")
                    : "model render-target clear must disable scissor before clearing the internal FBO";
            assert generated.contains("Same-texture framebuffer feedback flickers on some desktop drivers")
                    && generated.contains("use the SDK's two-target copy path.\n    return false;")
                    : "desktop integration must use double-buffer copies instead of same-texture barrier feedback";
            assert generated.contains("glDisable(GL_COLOR_LOGIC_OP);")
                    : "Cubism rendering must disable host color logic operations";
            assert generated.contains("_modelRenderTargets[0].EndDraw();\n    glDisable(GL_CULL_FACE);")
                    : "the final model render-target quad must not inherit drawable or host culling";
        }
        String hudText = Files.readString(hud);
        assert hudText.contains("RenderGuiEvent.Post");
        assert hudText.contains("beginPreview(boolean showWorldHud)");
        assert hudText.contains("renderPreview(");
        assert !hudText.contains("queuePreview(") && !hudText.contains("queuedPreview")
                : "screen preview must not cross a frame boundary through a HUD queue";
        assert hudText.contains("endPreview()");
        assert !hudText.contains("Live2D GL SNAPSHOT") && !hudText.contains("glStateSnapshot()")
                : "temporary GL snapshot diagnostics must be removed";
        assert hudText.contains("LIFECYCLE");
        assert !hudText.contains("new Live2DClientState()") : "preview must not own a second state";
        assert !hudText.contains("RenderGuiOverlayEvent");
        assert !hudText.contains("VanillaGuiOverlay");
        assert !hudText.contains("getOverlay()");
        assert hudText.split("state\\.renderWorld\\(event\\.getPartialTick\\(\\),", -1).length == 2
                : "whole-HUD post event must contain exactly one Live2D render call";
        assert hudText.contains("ClientPlayerNetworkEvent.LoggingOut");
        assert hudText.contains("ClientPlayerNetworkEvent.LoggingIn");
        assert hudText.contains("GameShuttingDownEvent");
        assert hudText.contains("RenderSystem.assertOnRenderThread");
        assert hudText.split(java.util.regex.Pattern.quote("fullBounds("), -1).length >= 3
                : "adjust draw must use a stable full-screen quad";
        assert hudText.indexOf("event.getGuiGraphics().flush()") < hudText.indexOf("state.renderWorld(event.getPartialTick(),")
                : "queued GuiGraphics work must flush before native rendering";
        assert hudText.contains("drawShadow(") : "renderer must draw the soft shadow";
        assert hudText.contains("switchFadeTicks") && hudText.contains("fadeTicksRemaining")
                : "model switch fade must be wired";
        assert hudText.contains("lastWorldModelId")
                && hudText.contains("!lastWorldModelId.equals(state.activeModelId())")
                && hudText.contains("lastWorldModelId = state.activeModelId()")
                : "world model fade must track its own id independent of preview frames";
        String compositorText = Files.readString(compositor);
        assert compositorText.contains("GameRenderer::getPositionTexShader");
        assert compositorText.contains("RenderSystem.setShaderTexture(0, frame.textureId())");
        assert compositorText.contains("SourceFactor.ONE") && compositorText.contains("DestFactor.ONE_MINUS_SRC_ALPHA");
        assert compositorText.contains("DefaultVertexFormat.POSITION_TEX");
        assert compositorText.contains("vertex(matrix, bounds.left(),  bounds.bottom(), 0).uv(bounds.u0(), bounds.v0())")
                && compositorText.contains("vertex(matrix, bounds.right(), bounds.bottom(), 0).uv(bounds.u1(), bounds.v0())")
                && compositorText.contains("vertex(matrix, bounds.right(), bounds.top(),    0).uv(bounds.u1(), bounds.v1())")
                && compositorText.contains("vertex(matrix, bounds.left(),  bounds.top(),    0).uv(bounds.u0(), bounds.v1())")
                : "compositor must map the clamped framebuffer rect into GUI coordinates with corrected V";
        assert !compositorText.contains("DynamicTexture") && !compositorText.contains("TextureManager")
                && !compositorText.contains("NativeImage") && !compositorText.contains("glDeleteTextures")
                : "compositor must borrow, not own, the Native texture";
        int compositorDrawMethod = compositorText.indexOf("static void draw(");
        int compositorTry = compositorText.indexOf("try {", compositorDrawMethod);
        int compositorFirstMutation = compositorText.indexOf("RenderSystem.disableDepthTest();");
        int compositorFinally = compositorText.indexOf("} finally {", compositorFirstMutation);
        int compositorDraw = compositorText.indexOf("BufferUploader.drawWithShader", compositorTry);
        assert compositorDrawMethod >= 0 && compositorTry >= 0 && compositorFirstMutation > compositorTry
                && compositorDraw > compositorFirstMutation && compositorFinally > compositorDraw
                && compositorText.indexOf("RenderSystem.defaultBlendFunc();", compositorFinally) > compositorFinally
                && compositorText.indexOf("RenderSystem.disableBlend();", compositorFinally) > compositorFinally
                && compositorText.indexOf("RenderSystem.setShaderColor(1, 1, 1, 1);", compositorFinally) > compositorFinally
                && compositorText.indexOf("RenderSystem.enableDepthTest();", compositorFinally) > compositorFinally
                : "compositor mutations and draw must be guarded by finally cleanup";
        int worldRender = hudText.indexOf("state.renderWorld(event.getPartialTick(),");
        int worldDraw = hudText.indexOf("Live2DTextureRenderer.draw(", worldRender);
        int worldReturn = hudText.indexOf("return result;", worldDraw);
        assert worldRender >= 0 && worldDraw > worldRender && worldReturn > worldDraw
                : "world texture must be consumed immediately in the frame lambda";
        int worldDrawLineEnd = hudText.indexOf(";", worldDraw);
        assert !hudText.substring(worldRender, worldReturn).contains("fullBounds(")
                && hudText.substring(worldDraw, worldDrawLineEnd).contains("scaledFullBounds(")
                : "world texture must be drawn exactly once with scaled bounds";
        int previewMethod = hudText.indexOf("renderPreview(GuiGraphics graphics,");
        int previewDraw = hudText.indexOf("Live2DTextureRenderer.draw(", previewMethod);
        int previewLambdaEnd = hudText.indexOf("});", previewDraw);
        assert previewMethod >= 0 && previewDraw > previewMethod && previewLambdaEnd > previewDraw
                : "preview texture must be consumed immediately in its own frame lambda";
        assert hudText.contains("state.modelBounds(")
                && hudText.contains("preview, guiWidth, guiHeight, framebufferWidth, framebufferHeight")
                : "preview bounds must derive from the preview frame transform";
        String screenText = Files.readString(project.resolve("src/main/java/com/yuan/live2d/client/gui/Live2DConfigScreen.java"));
        assert screenText.contains("Live2DHudRenderer.renderPreview(g, new Live2DClientState.PreviewFrame(")
                : "standalone screen must render the native preview";
        assert screenText.contains("Live2DHudRenderer.endPreview()") : "screen must release preview on close";
        assert screenText.contains("store.save(config)") : "screen must persist config changes";
        assert !screenText.contains("Live2DConsolePage") && !screenText.contains("Live2DConsoleLayout")
                : "standalone screen must not reference legacy console classes";
        assert hudText.contains("RenderSystem.isOnRenderThread()") && hudText.contains("RenderSystem.recordRenderCall")
                : "cleanup must run directly on the render thread and queue only when needed";
        assert hudText.contains("Runtime.getRuntime().addShutdownHook") && hudText.contains("requestClose(true)")
                : "JVM shutdown may only request render-thread cleanup";
        String stateText = Files.readString(project.resolve("src/main/java/com/yuan/live2d/client/live2d/Live2DClientState.java"));
        assert !stateText.contains("clipWidth()") : "PreviewFrame must not retain clip dimensions";
        assert stateText.contains("clampPlacement(") : "render must clamp offscreen models into view";
        String modelHeaderText = Files.readString(project.resolve("native/live2d/src/YuanLive2DModel.hpp"));
        assert !modelHeaderText.contains("ClipRect") && !modelText.contains("ClipRect")
                : "offscreen model must expose one concrete Draw API";
        assert stateText.contains("Live2D READY model-created");
        assert stateText.contains("Live2D DISABLED");
        assert stateText.contains("Live2D RETRY runtime-unavailable");
        assert stateText.contains("Live2D RETRY model-create-failed");
        assert gradle.contains("tasks.register('verifyLive2DJarExclusions')");
        assert gradle.contains("dependsOn 'javaChecks'") : "javaChecks must be wired into :build";
        assert gradle.contains("runtime/windows-x86_64/yuan_live2d.dll")
                && gradle.contains("models/Haru/Haru.model3.json")
                && gradle.contains("runtime/windows-x86_64/FrameworkShaders/")
                && gradle.contains("runtime/windows-x86_64/THIRD_PARTY_NOTICES/");
        assert installerText.contains("Files.createTempDirectory");
        assert installerText.contains("runtime/windows-x86_64/yuan_live2d.dll");
        assert installerText.contains("models/Haru/Haru.model3.json");
        assert installerText.contains("traversalRejected");
        assert installerText.contains("legacyNeverOverwrites");
        assert !hudText.contains("private static final Live2DClientState STATE")
                : "client state must not load config during subscriber class initialization";
        Path pathsSource = project.resolve("src/main/java/com/yuan/live2d/client/live2d/Live2DPaths.java");
        String pathsText = Files.readString(pathsSource);
        assert pathsText.contains("resolve(\"yuan_live2d\")") : "standalone data root must be config/yuan_live2d";
        assert pathsText.contains("runtime/windows-x86_64")
                : "standalone runtime path must be config/yuan_live2d/runtime/windows-x86_64";
        assert Live2DPaths.runtime().toString().replace('\\', '/').endsWith("yuan_live2d/runtime/windows-x86_64")
                : "runtime path must resolve under config/yuan_live2d/runtime/windows-x86_64";
        Live2DClientStateCheck.check();
        Live2DRuntimeCheck.check();
    }
}
