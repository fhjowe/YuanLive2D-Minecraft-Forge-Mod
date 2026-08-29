#include "YuanLive2DNativeSupport.hpp"

#include <fstream>
#include <limits>
#include <stdexcept>
#include <cwchar>

namespace fs = std::filesystem;

namespace {
bool containsParent(const fs::path& path) {
    for (const auto& component : path)
        if (component == "..") return true;
    return false;
}

bool isContained(const fs::path& root, const fs::path& path) {
    auto rootPart = root.begin();
    auto pathPart = path.begin();
    for (; rootPart != root.end(); ++rootPart, ++pathPart)
        if (pathPart == path.end() || _wcsicmp(rootPart->c_str(), pathPart->c_str()) != 0) return false;
    return true;
}
}

fs::path YuanLive2DCanonicalRoot(const fs::path& root) {
    if (root.empty()) throw std::invalid_argument("Root path is empty");
    const auto canonical = fs::canonical(root);
    if (!fs::is_directory(canonical)) throw std::invalid_argument("Root path is not a directory");
    return canonical;
}

fs::path YuanLive2DCanonicalContained(const fs::path& root, const fs::path& path) {
    const auto canonical = fs::canonical(path);
    if (!isContained(root, canonical)) throw std::invalid_argument("Path escapes configured root");
    return canonical;
}

fs::path YuanLive2DResolveContained(const fs::path& root, const fs::path& relative) {
    if (relative.empty() || relative.is_absolute() || containsParent(relative))
        throw std::invalid_argument("Relative path is unsafe");
    return YuanLive2DCanonicalContained(root, root / relative);
}

std::vector<Csm::csmByte> YuanLive2DReadBytes(const fs::path& path) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input) throw std::runtime_error("Cannot open file: " + path.u8string());
    const auto end = input.tellg();
    if (end <= 0) throw std::runtime_error("Empty file: " + path.u8string());
    const auto maximum = static_cast<std::uintmax_t>(std::numeric_limits<Csm::csmSizeInt>::max());
    if (static_cast<std::uintmax_t>(end) > maximum) throw std::runtime_error("File exceeds Cubism size limit");
    std::vector<Csm::csmByte> bytes(static_cast<size_t>(end));
    input.seekg(0);
    if (!input.read(reinterpret_cast<char*>(bytes.data()), static_cast<std::streamsize>(end)))
        throw std::runtime_error("Cannot read file: " + path.u8string());
    return bytes;
}

std::int64_t YuanLive2DTakeHandle(std::int64_t& next) {
    if (next <= 0 || next == std::numeric_limits<std::int64_t>::max())
        throw std::runtime_error("Live2D handle space exhausted");
    return next++;
}

size_t YuanLive2DBoundedUtf8Length(std::string_view message, size_t maximum) noexcept {
    size_t length = message.size() < maximum ? message.size() : maximum;
    if (length < message.size())
        while (length && (static_cast<unsigned char>(message[length]) & 0xC0) == 0x80) --length;
    return length;
}
