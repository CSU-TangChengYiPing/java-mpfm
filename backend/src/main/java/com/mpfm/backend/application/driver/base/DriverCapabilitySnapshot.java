package com.mpfm.backend.application.driver.base;

/**
 * 驱动能力快照：统一承载 OpenList 风格的协议能力与传输策略语义。
 */
public record DriverCapabilitySnapshot(
        String protocol,
        boolean list,
        boolean get,
        boolean link,
        boolean put,
        boolean remove,
        boolean rename,
        boolean move,
        boolean copy,
        boolean makeDir,
        boolean directUpload,
        boolean onlyProxy,
        boolean noLinkUrl,
        boolean preferProxy
) {
    /** 基于驱动能力与协议画像构建快照。 */
    public static DriverCapabilitySnapshot from(String protocol, DriverCapability capability) {
        ProtocolCapabilityProfile profile = ProtocolCapabilityProfile.fromProtocol(protocol);
        return new DriverCapabilitySnapshot(
                protocol == null ? "" : protocol,
                capability.list(),
                capability.get(),
                capability.link(),
                capability.put(),
                capability.remove(),
                capability.rename(),
                capability.move(),
                capability.copy(),
                capability.makeDir(),
                capability.directUpload(),
                profile.onlyProxy(),
                profile.noLinkUrl(),
                profile.preferProxy()
        );
    }

    /** 协议能力画像：对齐 OpenList `meta.go` 的代理策略字段。 */
    private record ProtocolCapabilityProfile(boolean onlyProxy, boolean noLinkUrl, boolean preferProxy) {
        static ProtocolCapabilityProfile fromProtocol(String protocol) {
            if ("local".equalsIgnoreCase(protocol) || "sftp".equalsIgnoreCase(protocol)) {
                return new ProtocolCapabilityProfile(true, true, false);
            }
            if ("webdav".equalsIgnoreCase(protocol)) {
                return new ProtocolCapabilityProfile(false, false, true);
            }
            return new ProtocolCapabilityProfile(false, false, false);
        }
    }
}

