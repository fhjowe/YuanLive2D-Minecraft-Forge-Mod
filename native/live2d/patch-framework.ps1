param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination
)

$ErrorActionPreference = 'Stop'

function Replace-Exact([string]$File, [string]$Old, [string]$New) {
    $text = [IO.File]::ReadAllText($File)
    $count = ([regex]::Matches($text, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one SDK source match in $File, found $count" }
    [IO.File]::WriteAllText($File, $text.Replace($Old, $New), [Text.UTF8Encoding]::new($false))
}

if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
Copy-Item -LiteralPath $Source -Destination $Destination -Recurse

$rendererH = Join-Path $Destination 'src/Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp'
$rendererCpp = Join-Path $Destination 'src/Rendering/OpenGL/CubismRenderer_OpenGLES2.cpp'
$shaderH = Join-Path $Destination 'src/Rendering/OpenGL/CubismShader_OpenGLES2.hpp'
$shaderCpp = Join-Path $Destination 'src/Rendering/OpenGL/CubismShader_OpenGLES2.cpp'
$openGlRoot = Join-Path $Destination 'src/Rendering/OpenGL'

Replace-Exact $rendererCpp '#ifdef CSM_TARGET_WINGL' '#ifdef CSM_TARGET_WIN_GL'

Replace-Exact $rendererCpp @'
    const csmUint16 ModelRenderTargetIndexArray[] = {
        0, 1, 2,
        2, 1, 3,
    };
'@ @'
    const csmUint16 ModelRenderTargetIndexArray[] = {
        0, 1, 2,
        2, 1, 3,
    };
    const csmFloat32 renderTargetVertexArray[] = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
    const csmFloat32 renderTargetUvArray[] = {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f};
    const csmFloat32 renderTargetReverseUvArray[] = {0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f};
'@

Replace-Exact $rendererH @'
    GLuint GetBindedTextureId(csmInt32 textureId);
'@ @'
    GLuint GetBindedTextureId(csmInt32 textureId);
private:
    void UploadDrawableBuffers(const CubismModel& model, csmInt32 index);
    void BindRenderTargetBuffers(csmBool reverseUv);
'@

Replace-Exact $rendererH @'
    GLint _modelRootFBO; ///< モデル描画のルートフレームバッファ
'@ @'
    GLint _modelRootFBO; ///< モデル描画のルートフレームバッファ
    csmVector<GLuint> _drawableVertexBuffers;
    csmVector<GLuint> _drawableUvBuffers;
    csmVector<GLuint> _drawableIndexBuffers;
    csmVector<csmBool> _drawableStaticUploaded;
    GLuint _renderTargetVertexBuffer;
    GLuint _renderTargetUvBuffer;
    GLuint _renderTargetReverseUvBuffer;
    GLuint _renderTargetIndexBuffer;
'@

Replace-Exact $shaderH @'
    void CopyTexture(
        GLint texture,
'@ @'
    void CopyTexture(
        CubismRenderer_OpenGLES2* renderer,
        GLint texture,
'@

Replace-Exact $shaderH @'
    void SetVertexAttributes(const CubismModel& model, const csmInt32 index, CubismShaderSet* shaderSet);
'@ @'
    void SetVertexAttributes(CubismRenderer_OpenGLES2* renderer, const CubismModel& model, const csmInt32 index, CubismShaderSet* shaderSet);
'@

Replace-Exact $rendererCpp @'
PFNGLBINDBUFFERPROC glBindBuffer;
'@ @'
PFNGLBINDBUFFERPROC glBindBuffer;
PFNGLGENBUFFERSPROC glGenBuffers;
PFNGLDELETEBUFFERSPROC glDeleteBuffers;
PFNGLBUFFERDATAPROC glBufferData;
'@

Replace-Exact $rendererCpp @'
    glBindBuffer = (PFNGLBINDBUFFERPROC)WinGlGetProcAddress("glBindBuffer");
'@ @'
    glBindBuffer = (PFNGLBINDBUFFERPROC)WinGlGetProcAddress("glBindBuffer");
    glGenBuffers = (PFNGLGENBUFFERSPROC)WinGlGetProcAddress("glGenBuffers");
    glDeleteBuffers = (PFNGLDELETEBUFFERSPROC)WinGlGetProcAddress("glDeleteBuffers");
    glBufferData = (PFNGLBUFFERDATAPROC)WinGlGetProcAddress("glBufferData");
'@

Replace-Exact $rendererCpp @'
    , _clippingContextBufferForOffscreen(NULL)
{
'@ @'
    , _clippingContextBufferForOffscreen(NULL)
    , _renderTargetVertexBuffer(0)
    , _renderTargetUvBuffer(0)
    , _renderTargetReverseUvBuffer(0)
    , _renderTargetIndexBuffer(0)
{
'@

Replace-Exact $rendererCpp @'
CubismRenderer_OpenGLES2::~CubismRenderer_OpenGLES2()
{
'@ @'
CubismRenderer_OpenGLES2::~CubismRenderer_OpenGLES2()
{
    if (_drawableVertexBuffers.GetSize()) glDeleteBuffers(_drawableVertexBuffers.GetSize(), &_drawableVertexBuffers[0]);
    if (_drawableUvBuffers.GetSize()) glDeleteBuffers(_drawableUvBuffers.GetSize(), &_drawableUvBuffers[0]);
    if (_drawableIndexBuffers.GetSize()) glDeleteBuffers(_drawableIndexBuffers.GetSize(), &_drawableIndexBuffers[0]);
    const GLuint buffers[] = {_renderTargetVertexBuffer, _renderTargetUvBuffer, _renderTargetReverseUvBuffer, _renderTargetIndexBuffer};
    glDeleteBuffers(sizeof(buffers) / sizeof(buffers[0]), buffers);
'@

Replace-Exact $rendererCpp @'
void CubismRenderer_OpenGLES2::Initialize(CubismModel* model, csmInt32 maskBufferCount)
{
'@ @'
void CubismRenderer_OpenGLES2::Initialize(CubismModel* model, csmInt32 maskBufferCount)
{
#ifdef CSM_TARGET_WIN_GL
    InitializeGlFunctions();
    if (!s_isInitializeGlFunctionsSuccess)
    {
        CubismLogError("Core profile buffer functions are unavailable.");
        return;
    }
#endif
    const csmInt32 drawableCount = model->GetDrawableCount();
    _drawableVertexBuffers.Resize(drawableCount);
    _drawableUvBuffers.Resize(drawableCount);
    _drawableIndexBuffers.Resize(drawableCount);
    _drawableStaticUploaded.Resize(drawableCount);
    glGenBuffers(drawableCount, &_drawableVertexBuffers[0]);
    glGenBuffers(drawableCount, &_drawableUvBuffers[0]);
    glGenBuffers(drawableCount, &_drawableIndexBuffers[0]);
    GLuint buffers[4]{};
    glGenBuffers(4, buffers);
    _renderTargetVertexBuffer = buffers[0];
    _renderTargetUvBuffer = buffers[1];
    _renderTargetReverseUvBuffer = buffers[2];
    _renderTargetIndexBuffer = buffers[3];
    glBindBuffer(GL_ARRAY_BUFFER, _renderTargetVertexBuffer);
    glBufferData(GL_ARRAY_BUFFER, sizeof(renderTargetVertexArray), renderTargetVertexArray, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, _renderTargetUvBuffer);
    glBufferData(GL_ARRAY_BUFFER, sizeof(renderTargetUvArray), renderTargetUvArray, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, _renderTargetReverseUvBuffer);
    glBufferData(GL_ARRAY_BUFFER, sizeof(renderTargetReverseUvArray), renderTargetReverseUvArray, GL_STATIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, _renderTargetIndexBuffer);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(ModelRenderTargetIndexArray), ModelRenderTargetIndexArray, GL_STATIC_DRAW);
'@

Replace-Exact $rendererCpp @'
void CubismRenderer_OpenGLES2::DrawMeshOpenGL(const CubismModel& model, const csmInt32 index)
'@ @'
void CubismRenderer_OpenGLES2::UploadDrawableBuffers(const CubismModel& model, const csmInt32 index)
{
    const csmInt32 vertexCount = model.GetDrawableVertexCount(index);
    const csmInt32 indexCount = model.GetDrawableVertexIndexCount(index);
    glBindBuffer(GL_ARRAY_BUFFER, _drawableVertexBuffers[index]);
    if (!_drawableStaticUploaded[index] || model.GetDrawableDynamicFlagVertexPositionsDidChange(index))
        glBufferData(GL_ARRAY_BUFFER, vertexCount * 2 * sizeof(csmFloat32), model.GetDrawableVertices(index), GL_STREAM_DRAW);
    if (!_drawableStaticUploaded[index])
    {
        glBindBuffer(GL_ARRAY_BUFFER, _drawableUvBuffers[index]);
        glBufferData(GL_ARRAY_BUFFER, vertexCount * 2 * sizeof(csmFloat32), model.GetDrawableVertexUvs(index), GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, _drawableIndexBuffers[index]);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexCount * sizeof(csmUint16), model.GetDrawableVertexIndices(index), GL_STATIC_DRAW);
        _drawableStaticUploaded[index] = true;
    }
    else glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, _drawableIndexBuffers[index]);
}

void CubismRenderer_OpenGLES2::BindRenderTargetBuffers(const csmBool reverseUv)
{
    glBindBuffer(GL_ARRAY_BUFFER, _renderTargetVertexBuffer);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, _renderTargetIndexBuffer);
    glBindBuffer(GL_ARRAY_BUFFER, reverseUv ? _renderTargetReverseUvBuffer : _renderTargetUvBuffer);
}

void CubismRenderer_OpenGLES2::DrawMeshOpenGL(const CubismModel& model, const csmInt32 index)
'@

Replace-Exact $rendererCpp @'
    glEnable(GL_BLEND);
    glColorMask(1, 1, 1, 1);
'@ @'
    glEnable(GL_BLEND);
    glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
    glDisable(GL_COLOR_LOGIC_OP);
    glColorMask(1, 1, 1, 1);
'@

Replace-Exact $rendererCpp @'
csmBool CubismRenderer_OpenGLES2::CanUseTextureBarrier()
{
'@ @'
csmBool CubismRenderer_OpenGLES2::CanUseTextureBarrier()
{
    // Same-texture framebuffer feedback flickers on some desktop drivers; use the SDK's two-target copy path.
    return false;
#if 0
'@

Replace-Exact $rendererCpp @'
    return false;
}

void CubismRenderer_OpenGLES2::Initialize(CubismModel* model)
'@ @'
    return false;
#endif
}

void CubismRenderer_OpenGLES2::Initialize(CubismModel* model)
'@

Replace-Exact $rendererCpp @'
    offscreen->GetRenderTarget()->BeginDraw(oldFBO);
    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);
'@ @'
    offscreen->GetRenderTarget()->BeginDraw(oldFBO);
    _currentFBO = offscreen->GetRenderTarget()->GetRenderTexture();
    PreDraw();
    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);
'@

Replace-Exact $rendererCpp @'
    // 現在のオフスクリーンレンダリングターゲットを設定
    _currentOffscreen = offscreen;
    _currentFBO = offscreen->GetRenderTarget()->GetRenderTexture();
'@ @'
    // 現在のオフスクリーンレンダリングターゲットを設定
    _currentOffscreen = offscreen;
'@

Replace-Exact $rendererCpp @'
    _currentOffscreen = _currentOffscreen->GetOldOffscreen();
    _currentFBO = offscreen->GetRenderTarget()->GetOldFBO();

    CubismShader_OpenGLES2::GetInstance()->SetupShaderProgramForOffscreen(this, model, offscreen);
'@ @'
    _currentOffscreen = _currentOffscreen->GetOldOffscreen();
    _currentFBO = offscreen->GetRenderTarget()->GetOldFBO();
    PreDraw();

    CubismShader_OpenGLES2::GetInstance()->SetupShaderProgramForOffscreen(this, model, offscreen);
'@

Replace-Exact $rendererCpp @'
void CubismRenderer_OpenGLES2::BeforeDrawModelRenderTarget()
{
    if (_modelRenderTargets.GetSize() == 0)
    {
        return;
    }
'@ @'
void CubismRenderer_OpenGLES2::BeforeDrawModelRenderTarget()
{
    if (!GetModel()->IsBlendModeEnabled())
    {
        return;
    }
    if (_modelRenderTargets.GetSize() == 0)
    {
        return;
    }
'@

Replace-Exact $rendererCpp @'
void CubismRenderer_OpenGLES2::AfterDrawModelRenderTarget()
{
    if (_modelRenderTargets.GetSize() == 0)
    {
        return;
    }
'@ @'
void CubismRenderer_OpenGLES2::AfterDrawModelRenderTarget()
{
    if (!GetModel()->IsBlendModeEnabled())
    {
        return;
    }
    if (_modelRenderTargets.GetSize() == 0)
    {
        return;
    }
'@

Replace-Exact $rendererCpp @'
    _modelRenderTargets[0].EndDraw();

    CubismShader_OpenGLES2::GetInstance()->SetupShaderProgramForOffscreenRenderTarget(this);
'@ @'
    _modelRenderTargets[0].EndDraw();
    glDisable(GL_CULL_FACE);

    CubismShader_OpenGLES2::GetInstance()->SetupShaderProgramForOffscreenRenderTarget(this);
'@

Replace-Exact $rendererCpp @'
    _modelRenderTargets[0].BeginDraw();
    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);
'@ @'
    _modelRenderTargets[0].BeginDraw();
    PreDraw();
    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);
'@

Replace-Exact $rendererCpp @'
        csmInt32 indexCount = model.GetDrawableVertexIndexCount(index);
        csmUint16* indexArray = const_cast<csmUint16*>(model.GetDrawableVertexIndices(index));
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, indexArray);
'@ @'
        csmInt32 indexCount = model.GetDrawableVertexIndexCount(index);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, nullptr);
'@

$quadDraw = 'glDrawElements(GL_TRIANGLES, sizeof(ModelRenderTargetIndexArray) / sizeof(csmUint16), GL_UNSIGNED_SHORT, ModelRenderTargetIndexArray);'
$rendererText = [IO.File]::ReadAllText($rendererCpp)
$quadCount = ([regex]::Matches($rendererText, [regex]::Escape($quadDraw))).Count
if ($quadCount -ne 3) { throw "Expected exactly three SDK render-target draw matches in $rendererCpp, found $quadCount" }
[IO.File]::WriteAllText($rendererCpp, $rendererText.Replace($quadDraw, 'glDrawElements(GL_TRIANGLES, sizeof(ModelRenderTargetIndexArray) / sizeof(csmUint16), GL_UNSIGNED_SHORT, nullptr);'), [Text.UTF8Encoding]::new($false))

Replace-Exact $rendererCpp @'
    CubismShader_OpenGLES2::GetInstance()->CopyTexture(srcBuffer.GetColorBuffer());
'@ @'
    CubismShader_OpenGLES2::GetInstance()->CopyTexture(this, srcBuffer.GetColorBuffer());
'@

$shaderText = [IO.File]::ReadAllText($shaderCpp)
$setCount = ([regex]::Matches($shaderText, [regex]::Escape('SetVertexAttributes(model, index, shaderSet);'))).Count
if ($setCount -ne 2) { throw "Expected exactly two SDK vertex setup calls in $shaderCpp, found $setCount" }
[IO.File]::WriteAllText($shaderCpp, $shaderText.Replace('SetVertexAttributes(model, index, shaderSet);', 'SetVertexAttributes(renderer, model, index, shaderSet);'), [Text.UTF8Encoding]::new($false))

Replace-Exact $shaderCpp @'
    CopyTexture(texture, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, baseColor);
'@ @'
    CopyTexture(renderer, texture, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA, baseColor);
'@

Replace-Exact $shaderCpp @'
void CubismShader_OpenGLES2::CopyTexture(GLint texture, csmInt32 srcColor, csmInt32 dstColor, csmInt32 srcAlpha, csmInt32 dstAlpha, CubismRenderer::CubismTextureColor baseColor)
'@ @'
void CubismShader_OpenGLES2::CopyTexture(CubismRenderer_OpenGLES2* renderer, GLint texture, csmInt32 srcColor, csmInt32 dstColor, csmInt32 srcAlpha, csmInt32 dstAlpha, CubismRenderer::CubismTextureColor baseColor)
'@

Replace-Exact $shaderCpp @'
    // 頂点位置属性の設定
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, renderTargetVertexArray);

    // テクスチャ座標属性の設定
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, renderTargetUvArray);
'@ @'
    renderer->BindRenderTargetBuffers(false);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_renderTargetVertexBuffer);
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_renderTargetUvBuffer);
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
'@

Replace-Exact $shaderCpp @'
    // 頂点位置属性の設定
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, renderTargetVertexArray);

    // テクスチャ座標属性の設定
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, renderTargetReverseUvArray);
'@ @'
    renderer->BindRenderTargetBuffers(true);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_renderTargetVertexBuffer);
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_renderTargetReverseUvBuffer);
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
'@

Replace-Exact $shaderCpp @'
void CubismShader_OpenGLES2::SetVertexAttributes(const CubismModel& model, const csmInt32 index, CubismShaderSet* shaderSet)
{
    // 頂点位置属性の設定
    const csmFloat32* vertexArray = model.GetDrawableVertices(index);
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, vertexArray);

    // テクスチャ座標属性の設定
    const csmFloat32* uvArray = reinterpret_cast<const csmFloat32*>(model.GetDrawableVertexUvs(index));
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, uvArray);
}
'@ @'
void CubismShader_OpenGLES2::SetVertexAttributes(CubismRenderer_OpenGLES2* renderer, const CubismModel& model, const csmInt32 index, CubismShaderSet* shaderSet)
{
    renderer->UploadDrawableBuffers(model, index);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_drawableVertexBuffers[index]);
    glEnableVertexAttribArray(shaderSet->AttributePositionLocation);
    glVertexAttribPointer(shaderSet->AttributePositionLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
    glBindBuffer(GL_ARRAY_BUFFER, renderer->_drawableUvBuffers[index]);
    glEnableVertexAttribArray(shaderSet->AttributeTexCoordLocation);
    glVertexAttribPointer(shaderSet->AttributeTexCoordLocation, 2, GL_FLOAT, GL_FALSE, sizeof(csmFloat32) * 2, nullptr);
}
'@

function Get-BalancedCalls([string]$Text, [string]$Name) {
    $calls = @()
    foreach ($match in [regex]::Matches($Text, "\b$([regex]::Escape($Name))\s*\(")) {
        $open = $Text.IndexOf('(', $match.Index)
        $depth = 0
        for ($index = $open; $index -lt $Text.Length; $index++) {
            if ($Text[$index] -eq '(') { $depth++ }
            elseif ($Text[$index] -eq ')' -and --$depth -eq 0) {
                $calls += $Text.Substring($open + 1, $index - $open - 1)
                break
            }
        }
        if ($depth -ne 0) { throw "Unbalanced $Name call in generated patched source" }
    }
    return $calls
}

function Split-CallArguments([string]$Call) {
    $arguments = @()
    $start = 0
    $depth = 0
    for ($index = 0; $index -lt $Call.Length; $index++) {
        if ($Call[$index] -eq '(') { $depth++ }
        elseif ($Call[$index] -eq ')') { $depth-- }
        elseif ($Call[$index] -eq ',' -and $depth -eq 0) {
            $arguments += $Call.Substring($start, $index - $start).Trim()
            $start = $index + 1
        }
    }
    $arguments += $Call.Substring($start).Trim()
    return $arguments
}

function Test-IntegerLiteral([string]$Value) {
    return $Value -match '^(?:0[xX][0-9a-fA-F]+|[0-9]+)(?i:(?:u(?:ll?)?|ll?u?))?$'
}

function Test-IntegerOffsetExpression([string]$Expression) {
    $operand = '[A-Za-z_]\w*(?:(?:\s+|\s*::\s*)[A-Za-z_]\w*)*(?:\s*[*&]\s*)*'
    $value = [regex]::Replace($Expression, "\boffsetof\s*\(\s*$operand\s*,\s*[A-Za-z_]\w*\s*\)", '1')
    $value = [regex]::Replace($value, "\bsizeof\s*\(\s*$operand\s*\)", '1')
    $value = $value -replace '\s', ''
    if (!$value) { return $false }

    $depth = 0
    $expectOperand = $true
    for ($index = 0; $index -lt $value.Length;) {
        if ($expectOperand) {
            if ($value[$index] -in @('+', '-')) { $index++; continue }
            if ($value[$index] -eq '(') { $depth++; $index++; continue }
            $literal = [regex]::Match($value.Substring($index), '^(?:0[xX][0-9a-fA-F]+|[0-9]+)(?i:(?:u(?:ll?)?|ll?u?))?')
            if (!$literal.Success) { return $false }
            $index += $literal.Length
            $expectOperand = $false
            continue
        }
        if ($value[$index] -eq ')' -and $depth -gt 0) { $depth--; $index++; continue }
        $operator = @('<<', '>>', '+', '-', '*', '/', '%', '&', '|') |
                Where-Object { $value.Substring($index).StartsWith($_) } | Select-Object -First 1
        if (!$operator) { return $false }
        $index += $operator.Length
        $expectOperand = $true
    }
    return !$expectOperand -and $depth -eq 0
}

function Test-BufferOffset([string]$Pointer) {
    $pointer = $Pointer.Trim()
    if ($pointer -in @('nullptr', '0') -or (Test-IntegerLiteral $pointer)) { return $true }
    if ($pointer -match '^reinterpret_cast<\s*const\s+void\s*\*>\s*\((.*)\)$') {
        return Test-IntegerOffsetExpression $Matches[1]
    }
    if ($pointer -match '^\(\s*const\s+void\s*\*\s*\)\s*\((.*)\)$') {
        return Test-IntegerOffsetExpression $Matches[1]
    }
    return $false
}

foreach ($safe in @(
    'nullptr',
    '0',
    '16',
    '0x20ULL',
    'reinterpret_cast<const void*>(sizeof(Vertex) * 2 + offsetof(Vertex, uv))',
    '(const void*)((sizeof(Vertex) << 1) | 4u)'
)) {
    if (!(Test-BufferOffset $safe)) { throw "Buffer offset self-check rejected safe expression: $safe" }
}
foreach ($unsafe in @(
    'vertexArray',
    'cpuPointer + 4',
    'reinterpret_cast<const void*>(vertexArray)',
    'reinterpret_cast<const void*>(sizeof(Vertex) + cpuPointer)',
    '(const void*)(offset + sizeof(Vertex))',
    'reinterpret_cast<const void*>(getOffset())',
    'static_cast<const void*>(16)'
)) {
    if (Test-BufferOffset $unsafe) { throw "Buffer offset self-check accepted unsafe expression: $unsafe" }
}

foreach ($file in Get-ChildItem -LiteralPath $openGlRoot -Filter '*.cpp') {
    $file = $file.FullName
    $text = [IO.File]::ReadAllText($file)
    foreach ($call in Get-BalancedCalls $text 'glVertexAttribPointer') {
        $arguments = Split-CallArguments $call
        if ($arguments.Count -ne 6) { throw "Unexpected glVertexAttribPointer syntax in generated patched source: $file" }
        $pointer = $arguments[5]
        if (!(Test-BufferOffset $pointer)) {
            throw "Unexpected glVertexAttribPointer pointer argument in generated patched source: ${file}: $pointer"
        }
    }
    foreach ($call in Get-BalancedCalls $text 'glDrawElements') {
        $arguments = Split-CallArguments $call
        if ($arguments.Count -ne 4) { throw "Unexpected glDrawElements syntax in generated patched source: $file" }
        $pointer = $arguments[3]
        if (!(Test-BufferOffset $pointer)) {
            throw "CPU index pointer remains in generated patched source: ${file}: $pointer"
        }
    }
}

$generatedRenderer = [IO.File]::ReadAllText($rendererCpp)
if (!$generatedRenderer.Contains('glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);') -or
        !$generatedRenderer.Contains("csmBool CubismRenderer_OpenGLES2::CanUseTextureBarrier()`n{`n    // Same-texture framebuffer feedback flickers on some desktop drivers; use the SDK's two-target copy path.`n    return false;") -or
        !$generatedRenderer.Contains("_modelRenderTargets[0].BeginDraw();`n    PreDraw();`n    glViewport(0, 0, _modelRenderTargetWidth, _modelRenderTargetHeight);")) {
    throw 'Generated Framework core-profile and model clear safety patches are incomplete'
}

Set-Content -LiteralPath (Join-Path $Destination 'YuanCoreProfilePatched') -Value 'CubismSdkForNative-5-r.5 core-profile VBO/EBO patch' -Encoding ascii
