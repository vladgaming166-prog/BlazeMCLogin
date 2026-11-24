package net.blazemc.login;

public final class PluginConstants {

    public static final String ID = "blazemclogin";
    public static final String NAME = "BlazeMCLogin";
    public static final String VERSION = "1.0.0";
    public static final String AUTHOR = "BlazeMC Network";
    public static final String WEBSITE = "https://mc.blazemc.ro";

    public static final String CHANNEL_NAMESPACE = "blazemclogin";
    public static final String CHANNEL_NAME = "v1";
    public static final String CHANNEL_ID = CHANNEL_NAMESPACE + ":" + CHANNEL_NAME;

    public static final String DEFAULT_PREFIX =
            "&#00FB5C&lB&#17FB6B&ll&#2EFC7A&la&#46FC88&lz&#5DFC97&le&#74FDA6&lM&#8BFDB5&lC "
                    + "&#A2FEC4&lL&#B9FED3&lo&#D1FEE1&lg&#E8FFF0&li&#FFFFFF&ln &r&7∙ ";

    public static final String DEFAULT_PREMIUM_AUTO_LOGIN =
            DEFAULT_PREFIX + "Logat automat cu premium!";

    public static final String PERM_ADMIN = "blazemclogin.admin";
    public static final String PERM_RELOAD = "blazemclogin.reload";
    public static final String PERM_INFO = "blazemclogin.info";
    public static final String PERM_ACCOUNTS = "blazemclogin.accounts";
    public static final String PERM_UNREGISTER = "blazemclogin.unregister";
    public static final String PERM_RESETPASSWORD = "blazemclogin.resetpassword";
    public static final String PERM_LOCK = "blazemclogin.lock";
    public static final String PERM_SESSION = "blazemclogin.session";
    public static final String PERM_BACKUP = "blazemclogin.backup";
    public static final String PERM_MIGRATE = "blazemclogin.migrate";
    public static final String PERM_LANGUAGE = "blazemclogin.language";
    public static final String PERM_BYPASS_IP = "blazemclogin.bypass.iplimit";
    public static final String PERM_BYPASS_LOGIN = "blazemclogin.bypass.login";

    public static final byte PROTOCOL_VERSION = 1;
    public static final int MAGIC = 0x424D4C31; // BML1

    private PluginConstants() {
    }
}
