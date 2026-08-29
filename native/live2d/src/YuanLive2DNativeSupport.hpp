#pragma once

#include <CubismFramework.hpp>
#include <cstdint>
#include <filesystem>
#include <exception>
#include <memory>
#include <string_view>
#include <vector>

std::filesystem::path YuanLive2DCanonicalRoot(const std::filesystem::path& root);
std::filesystem::path YuanLive2DCanonicalContained(const std::filesystem::path& root,
                                                   const std::filesystem::path& path);
std::filesystem::path YuanLive2DResolveContained(const std::filesystem::path& root,
                                                 const std::filesystem::path& relative);
std::vector<Csm::csmByte> YuanLive2DReadBytes(const std::filesystem::path& path);
std::int64_t YuanLive2DTakeHandle(std::int64_t& next);
size_t YuanLive2DBoundedUtf8Length(std::string_view message, size_t maximum) noexcept;

template<class Resource, class Check, class Publish>
void YuanLive2DCommitCreate(std::unique_ptr<Resource>& resource, Check&& check, Publish&& publish) {
    check();
    publish(std::move(resource));
}

struct YuanLive2DFinalCleanupState {
    bool modelOwned;
    bool frameworkActive;
    std::uint32_t vao;
    bool checked = false;
};

template<class CloseModel, class StopFramework, class DeleteVao, class Check>
void YuanLive2DAdvanceFinalCleanup(YuanLive2DFinalCleanupState& state, CloseModel&& closeModel,
                                   StopFramework&& stopFramework, DeleteVao&& deleteVao, Check&& check) {
    std::exception_ptr firstFailure;
    const auto attempt = [&](auto&& operation, auto&& complete) {
        try {
            operation();
            complete();
        } catch (...) {
            if (!firstFailure) firstFailure = std::current_exception();
        }
    };
    if (state.modelOwned) {
        attempt(closeModel, [&] { state.modelOwned = false; });
    }
    // Framework/VAO/check depend on the model renderer being conclusively gone.
    if (!state.modelOwned) {
        if (state.frameworkActive)
            attempt(stopFramework, [&] { state.frameworkActive = false; });
        if (state.vao) {
            const std::uint32_t vao = state.vao;
            attempt([&] { deleteVao(vao); }, [&] { state.vao = 0; });
        }
        if (!state.checked)
            attempt(check, [&] { state.checked = true; });
    }
    if (firstFailure) std::rethrow_exception(firstFailure);
}

inline bool YuanLive2DCanErase(const YuanLive2DFinalCleanupState& state) noexcept {
    return !state.modelOwned && !state.frameworkActive && state.vao == 0 && state.checked;
}
