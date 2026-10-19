package com.pyding.trade_hub;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TradeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_LISTINGS;
    public static final ForgeConfigSpec.IntValue MAX_AMOUNT;
    public static final ForgeConfigSpec.ConfigValue<String> PREMIUM_URL;
    public static final ForgeConfigSpec.IntValue PREMIUM_REFRESH_MINUTES;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_LISTINGS = b.defineInRange("maxActiveListings", 10, 1, 1000);
        MAX_AMOUNT = b.defineInRange("maxItemAmount", 100000, 1, 1000000);
        PREMIUM_URL = b.define("premiumNamesUrl", "");
        PREMIUM_REFRESH_MINUTES = b.defineInRange("premiumRefreshMinutes", 60, 5, 1440);
        SPEC = b.build();
    }
    private TradeConfig() {}
}
