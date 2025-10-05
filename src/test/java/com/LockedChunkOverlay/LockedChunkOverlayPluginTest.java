package com.LockedChunkOverlay;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LockedChunkOverlayPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LockedChunkOverlayPlugin.class);
		RuneLite.main(args);
	}
}