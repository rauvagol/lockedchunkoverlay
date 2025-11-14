package com.LockedChunkOverlay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("LockedChunkOverlay")
public interface LockedChunkOverlayConfig extends Config
{
    @ConfigItem(
        keyName = "darknessLevel",
        name = "Overlay darkness",
        description = "How dark forbidden chunks are rendered",
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
        return false;
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

    @ConfigItem(
        keyName = "allowedChunksSource",
        name = "Chunk Source",
        description = "Where to read allowed chunks from",
        position = 6
    )
    default AllowedChunksSource allowedChunksSource()
    {
        return AllowedChunksSource.MANUAL_INPUT;
    }

	@ConfigItem(
		keyName = "enableSiteAccess",
		name = "Enable site access",
		description = "Allow the plugin to make network requests to fetch chunk data from external servers",
		warning = "If you enable this, the plugin will make network requests and transmit your IP address to a 3rd-party server not controlled or verified by RuneLite developers.",
		position = 7
	)
	default boolean enableSiteAccess()
	{
		return false;
	}

	@ConfigItem(
		keyName = "manualChunksCsv",
		name = "Manual chunk IDs (comma separated)",
		description = "Manually specified allowed chunks",
		position = 8
	)
	default String manualChunksCsv()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chunkpickerMapCode",
		name = "Chunk Picker map code",
		description = "Map code used by Chunk Picker",
		position = 9
	)
	default String chunkpickerMapCode()
	{
		return "";
	}

    // No toggle; fetching is controlled by dropdown selection
}
