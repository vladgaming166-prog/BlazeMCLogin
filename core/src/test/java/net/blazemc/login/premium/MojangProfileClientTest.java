package net.blazemc.login.premium;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MojangProfileClientTest {

    @Test
    void invalidUsernamesAreNotLookedUp() {
        MojangProfileClient client = new MojangProfileClient();
        assertThat(client.lookup("")).isEqualTo(MinecraftProfileLookup.ProfileStatus.CRACKED);
        assertThat(client.lookup("thisnameiswaytoolong")).isEqualTo(MinecraftProfileLookup.ProfileStatus.CRACKED);
        assertThat(client.lookup("bad name")).isEqualTo(MinecraftProfileLookup.ProfileStatus.CRACKED);
        assertThat(client.lookup("evil;drop")).isEqualTo(MinecraftProfileLookup.ProfileStatus.CRACKED);
    }

    @Test
    void cacheDoesNotElevateCrackedToPremium() {
        MojangProfileClient client = new MojangProfileClient();
        client.putCache("steve", MinecraftProfileLookup.ProfileStatus.CRACKED);
        assertThat(client.lookup("Steve")).isEqualTo(MinecraftProfileLookup.ProfileStatus.CRACKED);
    }
}
