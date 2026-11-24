package net.blazemc.login.config;

import net.blazemc.login.PluginConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestrictionAndPermissionTest {

    @Test
    void allowedCommandsNormalizePluginPrefixAndSlash() {
        RestrictionSettings settings = RestrictionSettings.defaults();
        assertThat(settings.allowedCommandRoots()).contains("login", "register", "l", "reg");
        assertThat(RestrictionSettings.normalizeCommand("/minecraft:login extra")).isEqualTo("login");
        assertThat(RestrictionSettings.normalizeCommand("REGISTER")).isEqualTo("register");
        assertThat(settings.allowedCommandRoots()).doesNotContain("op", "gamemode");
    }

    @Test
    void permissionNodesMatchSpecification() {
        assertThat(PluginConstants.PERM_ADMIN).isEqualTo("blazemclogin.admin");
        assertThat(PluginConstants.PERM_RELOAD).isEqualTo("blazemclogin.reload");
        assertThat(PluginConstants.PERM_INFO).isEqualTo("blazemclogin.info");
        assertThat(PluginConstants.PERM_ACCOUNTS).isEqualTo("blazemclogin.accounts");
        assertThat(PluginConstants.PERM_UNREGISTER).isEqualTo("blazemclogin.unregister");
        assertThat(PluginConstants.PERM_RESETPASSWORD).isEqualTo("blazemclogin.resetpassword");
        assertThat(PluginConstants.PERM_LOCK).isEqualTo("blazemclogin.lock");
        assertThat(PluginConstants.PERM_SESSION).isEqualTo("blazemclogin.session");
        assertThat(PluginConstants.PERM_BYPASS_IP).isEqualTo("blazemclogin.bypass.iplimit");
        assertThat(PluginConstants.PERM_BYPASS_LOGIN).isEqualTo("blazemclogin.bypass.login");
    }
}
