package net.blazemc.login.config;

import net.blazemc.login.model.LanguageCode;

public final class PluginSettings {

    private volatile GeneralSettings general;
    private volatile SecuritySettings security;
    private volatile PremiumSettings premium;
    private volatile SessionSettings sessions;
    private volatile AccountLimitSettings accountLimits;
    private volatile RestrictionSettings restrictions;
    private volatile DatabaseSettings database;
    private volatile LoginSpawnSettings loginSpawn;

    public PluginSettings(
            GeneralSettings general,
            SecuritySettings security,
            PremiumSettings premium,
            SessionSettings sessions,
            AccountLimitSettings accountLimits,
            RestrictionSettings restrictions,
            DatabaseSettings database,
            LoginSpawnSettings loginSpawn
    ) {
        this.general = general;
        this.security = security;
        this.premium = premium;
        this.sessions = sessions;
        this.accountLimits = accountLimits;
        this.restrictions = restrictions;
        this.database = database;
        this.loginSpawn = loginSpawn;
    }

    public static PluginSettings defaults() {
        return new PluginSettings(
                GeneralSettings.defaults(),
                SecuritySettings.defaults(),
                PremiumSettings.defaults(),
                SessionSettings.defaults(),
                AccountLimitSettings.defaults(),
                RestrictionSettings.defaults(),
                DatabaseSettings.defaults(),
                LoginSpawnSettings.defaults()
        );
    }

    public GeneralSettings general() {
        return general;
    }

    public SecuritySettings security() {
        return security;
    }

    public PremiumSettings premium() {
        return premium;
    }

    public SessionSettings sessions() {
        return sessions;
    }

    public AccountLimitSettings accountLimits() {
        return accountLimits;
    }

    public RestrictionSettings restrictions() {
        return restrictions;
    }

    public DatabaseSettings database() {
        return database;
    }

    public LoginSpawnSettings loginSpawn() {
        return loginSpawn;
    }

    public LanguageCode language() {
        return general.language();
    }

    public void replace(PluginSettings other) {
        this.general = other.general;
        this.security = other.security;
        this.premium = other.premium;
        this.sessions = other.sessions;
        this.accountLimits = other.accountLimits;
        this.restrictions = other.restrictions;
        this.database = other.database;
        this.loginSpawn = other.loginSpawn;
    }
}
