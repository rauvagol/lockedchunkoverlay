package com.LockedChunkOverlay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("LockedChunkOverlay")
public interface LockedChunkOverlayConfig extends Config
{
    @ConfigItem(
		keyName = "screenFade",
		name = "Darken Screen",
        description = "Fade Screen to black when entering a locked chunk",
        position = 0
	)
	default boolean screenFade()
	{
		return true;
	}

	@ConfigItem(
		keyName = "opaqueAfterFive",
		name = "Completely cover screen",
        description = "Opaque overlay after 5 seconds in a locked chunk",
        position = 1
	)
	default boolean opaqueAfterFive()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fadeWarningSound",
		name = "Warning Sound",
        description = "Play an awful warning sound while the screen is fading",
        position = 2
	)
	default boolean fadeWarningSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugText",
		name = "Debug Text",
        description = "Show debug text in chat/overlay",
        position = 3
	)
	default boolean debugText()
	{
		return false;
	}
}
