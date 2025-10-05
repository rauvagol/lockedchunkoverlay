package com.LockedChunkOverlay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("LockedChunkOverlay")
public interface LockedChunkOverlayConfig extends Config
{
    @ConfigItem(
        keyName = "darknessLevel",
        name = "Forbidden chunk darkness",
        description = "How dark forbidden chunks render (tint to black)",
        position = 0
    )
    default DarknessLevel darknessLevel()
    {
        return DarknessLevel.VERY_DARK;
    }

    @ConfigItem(
        keyName = "fillChunks",
        name = "Fill chunks",
        description = "Fill the interior of forbidden chunks (unchecked: borders only)",
        position = 1
    )
    default boolean fillChunks()
    {
        return true;
    }

    @ConfigItem(
        keyName = "screenFade",
        name = "Darken Screen",
        description = "Fade Screen to black when entering a locked chunk",
        position = 2
    )
    default boolean screenFade()
    {
        return true;
    }

	@ConfigItem(
		keyName = "opaqueAfterFive",
		name = "Completely cover screen",
        description = "Opaque overlay after 5 seconds in a locked chunk",
        position = 3
	)
	default boolean opaqueAfterFive()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fadeWarningSound",
		name = "Warning Sound",
        description = "Play an awful warning sound while the screen is fading",
        position = 4
	)
	default boolean fadeWarningSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugText",
		name = "Debug Text",
        description = "Show debug text in chat/overlay",
        position = 5
	)
	default boolean debugText()
	{
		return false;
	}
}
