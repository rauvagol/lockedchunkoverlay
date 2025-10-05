package com.LockedChunkOverlay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface LockedChunkOverlayConfig extends Config
{
	@ConfigItem(
		keyName = "greeting",
		name = "Welcome Greeting",
		description = "The messasser when they login"
	)
	default String greeting()
	{
		return "Bello";
	}
}
