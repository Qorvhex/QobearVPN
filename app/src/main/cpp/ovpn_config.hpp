#pragma once

#include <openvpn/io/io.hpp>
#include <client/ovpncli.hpp>
#include <string>
#include <utility>

namespace qobear {

inline openvpn::ClientAPI::Config make_openvpn_config(std::string content) {
    openvpn::ClientAPI::Config config;
    config.content = std::move(content);
    config.guiVersion = "QobearVPN 1.0";
    config.connTimeout = 40;
    config.clockTickMS = 1000;
    config.tunPersist = true;
    config.retryOnAuthFailed = false;
    // Accept compressed packets from TunnelBear while never compressing outbound traffic (VORACLE mitigation).
    config.compressionMode = "asym";
    config.disableClientCert = config.content.find("<cert>") == std::string::npos;
    return config;
}

}  // namespace qobear
