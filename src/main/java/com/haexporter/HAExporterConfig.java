package com.haexporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("haexporter")
public interface HAExporterConfig extends Config {

    @ConfigItem(
            keyName = "WebhookUrl",
            name = "Webhook URL",
            description = "URL to send JSON data to for Home Assistant integration"
    )
    default String WebhookUrl() {
        return "";

    };

}