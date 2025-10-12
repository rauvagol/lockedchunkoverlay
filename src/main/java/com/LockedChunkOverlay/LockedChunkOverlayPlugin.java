package com.LockedChunkOverlay;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SoundEffectVolume;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Locked Chunk Overlay"
)
public class LockedChunkOverlayPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private LockedChunkOverlayConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlackoutOverlay blackoutOverlay;

	@Inject
	private ForbiddenTileOverlay forbiddenTileOverlay;

	private Integer lastRegionId;

	private boolean inLockedRegion;
	private long lockedEnterMs;
	private int lastRemainingSec = -1;
	private long lastGrueMs;
	private WorldPoint lastTile;
	private static final int RECT_WIDTH = 201; // tiles, perpendicular to direction
	private static final int RECT_LENGTH = 101; // tiles, along the direction
	private static final int WARNING_DISTANCE = 20; // tiles, warning distance from forbidden chunk border
    private long lastRegionsRefreshMs;
	private long lastChunkpickerFetchMs;
	private List<String> chunkpickerUnlocked = Collections.emptyList();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Locked Chunk Overlay started!");
		overlayManager.add(forbiddenTileOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Locked Chunk Overlay stopped!");
		lastTile = null;
		overlayManager.remove(forbiddenTileOverlay);
		forbiddenTileOverlay.setTiles(Collections.emptySet());
	}

private void printForbidden(String dir, WorldPoint wp)
{
	int rid = wp.getRegionID();
    int baseX = (rid >> 8) << 6; // southwest corner x of 64x64 region
    int baseY = (rid & 255) << 6; // southwest corner y
    int maxX = baseX + 63;
    int maxY = baseY + 63;
    if (config.debugText())
    {
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            dir + ": (" + wp.getX() + "," + wp.getY() + "," + wp.getPlane() + ") regionId=" + rid +
                " permitted=false border=(" + baseX + "," + baseY + ")-(" + maxX + "," + maxY + ")",
            null
        );
    }
}

@Subscribe
public void onWidgetClosed(WidgetClosed event)
{
    if (event.getGroupId() == InterfaceID.Worldmap.UNIVERSE) //not sure if this even does anything? but cant hurt
    {
        updateForbiddenRegionsFromLoadedRegions();
    }
}

@Subscribe
public void onConfigChanged(ConfigChanged event)
{
    if (!"LockedChunkOverlay".equals(event.getGroup()))
    {
        return;
    }
    if ("screenFade".equals(event.getKey()) && !config.screenFade())
    {
        // Immediately remove any blackout overlay when darken screen is turned off
        overlayManager.remove(blackoutOverlay);
        // Also reset countdown overlay state
        blackoutOverlay.setState(0, false);
    }
		// Refresh regions when manual list toggled/edited
		if ("useManualChunks".equals(event.getKey()) || "manualChunksCsv".equals(event.getKey()))
		{
			updateForbiddenRegionsFromLoadedRegions();
		}
		// Refresh and clear cache on chunkpicker changes
		if ("chunkpickerAutoFetch".equals(event.getKey()) || "chunkpickerMapCode".equals(event.getKey()))
		{
			chunkpickerUnlocked = Collections.emptyList();
			updateForbiddenRegionsFromLoadedRegions();
		}
}

private void addLocalInstance(Set<WorldPoint> out, WorldPoint wp)
{
	var locals = WorldPoint.toLocalInstance(client, wp);
	if (locals == null || locals.isEmpty())
	{
		out.add(wp);
		return;
	}
	out.addAll(locals);
}

	private String fetchChunkpickerUnlocked(String mapCode)
	{
		try
		{
			String url = "https://chunkpicker.firebaseio.com/maps/" + mapCode + "/chunks/unlocked.json";
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(3000);
			connection.setReadTimeout(3000);
			int status = connection.getResponseCode();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream(),
					StandardCharsets.UTF_8)))
			{
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null)
				{
					sb.append(line);
				}
				return sb.toString();
			}
		}
		catch (Exception e)
		{
			log.warn("Chunkpicker fetch failed", e);
			return null;
		}
	}

    private static final Pattern JSON_KEY_PATTERN = Pattern.compile("\"(\\d{1,6})\"\\s*:\\s*\"\\d{1,6}\"");

	private static List<String> parseChunkpickerKeys(String json)
	{
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		Matcher m = JSON_KEY_PATTERN.matcher(json);
		while (m.find())
		{
			out.add(m.group(1));
		}
		return out;
	}

	private List<String> resolveUnlockedRegions()
	{
		// Priority: 1) Chunk Picker (when enabled and cache available)
		if (config.chunkpickerAutoFetch())
		{
			String code = config.chunkpickerMapCode();
			if (code != null && !code.isBlank())
			{
				if (chunkpickerUnlocked != null && !chunkpickerUnlocked.isEmpty())
				{
					return chunkpickerUnlocked;
				}
			}
		}

		// 2) Manual CSV
		if (config.useManualChunks())
		{
			String csv = config.manualChunksCsv();
			return (csv == null || csv.isEmpty()) ? Collections.emptyList() : Text.fromCSV(csv);
		}

		// 3) Region Locker config
		String csv = configManager.getConfiguration("regionlocker", "unlockedRegions");
		return (csv == null || csv.isEmpty()) ? Collections.emptyList() : Text.fromCSV(csv);
	}

private WorldPoint nearestForbiddenWithin5(int px, int py, int pl, int dx, int dy, boolean hasList, List<String> unlocked)
{
	for (int d = 0; d <= WARNING_DISTANCE; d++)
	{
		WorldPoint t = new WorldPoint(px + dx * d, py + dy * d, pl);
		// Tiles outside mainland are always permitted
		if (!isWithinMainland(t))
		{
			continue;
		}
		int rid = t.getRegionID();
		boolean permitted = !hasList || unlocked.contains(Integer.toString(rid));
		if (!permitted)
		{
			return t;
		}
	}
	return null;
}

private boolean isWithinMainland(WorldPoint wp)
{
    int x = wp.getX();
    int y = wp.getY();
    return x >= 1024 && x <= 3967 && y >= 2496 && y <= 4159;
}

private void updateForbiddenRegionsFromLoadedRegions()
{
	int[] regions = client.getMapRegions();
	if (regions == null)
	{
		forbiddenTileOverlay.setTiles(Collections.emptySet());
		return;
	}

	Player lp = client.getLocalPlayer();
	WorldPoint wp = lp != null ? lp.getWorldLocation() : null;
	if (wp == null)
	{
		forbiddenTileOverlay.setTiles(Collections.emptySet());
		return;
	}

	// Outside mainland: treat all permitted
	if (!isWithinMainland(wp))
	{
		forbiddenTileOverlay.setTiles(Collections.emptySet());
		return;
	}

	List<String> unlocked = resolveUnlockedRegions();
	boolean hasList = !unlocked.isEmpty();

	Set<WorldPoint> tiles = new HashSet<>();
	int plane = client.getPlane();
	for (int rid : regions)
	{
		boolean permitted = !hasList || unlocked.contains(Integer.toString(rid));
		if (!permitted)
		{
			addRegionTiles(tiles, rid, plane);
		}
	}

	forbiddenTileOverlay.setTiles(tiles);
}

@Subscribe
public void onGameStateChanged(GameStateChanged gameStateChanged)
{
	if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
	{
		updateForbiddenRegionsFromLoadedRegions();
	}
}

private void addRectTiles(Set<WorldPoint> out, WorldPoint start, int dx, int dy, int length, int width)
{
	int halfW = Math.max(0, (width - 1) / 2);
	for (int i = 0; i < length; i++)
	{
		int baseX = start.getX() + dx * i;
		int baseY = start.getY() + dy * i;
		// Perpendicular vector
		int px = -dy;
		int py = dx;
		for (int j = -halfW; j <= halfW; j++)
		{
			WorldPoint p = new WorldPoint(baseX + px * j, baseY + py * j, start.getPlane());
			// Respect mainland rules: skip tiles outside mainland
			if (!isWithinMainland(p))
			{
				continue;
			}
			addLocalInstance(out, p);
		}
	}
}

private void addRectTilesOneSided(Set<WorldPoint> out, WorldPoint start, int dx, int dy, int length, int width, int sideX, int sideY)
{
    for (int i = 0; i < length; i++)
    {
        int baseX = start.getX() + dx * i;
        int baseY = start.getY() + dy * i;
        for (int j = 0; j < width; j++)
        {
            WorldPoint p = new WorldPoint(baseX + sideX * j, baseY + sideY * j, start.getPlane());
            if (!isWithinMainland(p))
            {
                continue;
            }
            addLocalInstance(out, p);
        }
    }
}

private void addRegionTiles(Set<WorldPoint> out, int regionId, int plane)
{
    int baseX = (regionId >> 8) << 6;
    int baseY = (regionId & 255) << 6;
    for (int rx = 0; rx < 64; rx++)
    {
        for (int ry = 0; ry < 64; ry++)
        {
            WorldPoint p = new WorldPoint(baseX + rx, baseY + ry, plane);
            if (!isWithinMainland(p))
            {
                continue;
            }
            addLocalInstance(out, p);
        }
    }
}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		int[] currentRegions = client.getMapRegions();
		if (currentRegions == null)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint worldPoint = localPlayer != null ? localPlayer.getWorldLocation() : null;
		if (worldPoint == null)
		{
			return;
		}

        long now = System.currentTimeMillis();
        if (now - lastRegionsRefreshMs >= 10000L)
        {
            updateForbiddenRegionsFromLoadedRegions();
            lastRegionsRefreshMs = now;
        }

        // Fetch from Chunk Picker every 10s when enabled and map code present
        if (config.chunkpickerAutoFetch() && now - lastChunkpickerFetchMs >= 10000L)
        {
            String code = config.chunkpickerMapCode();
            if (code != null && !code.isBlank())
            {
                String body = fetchChunkpickerUnlocked(code.trim());
                if (body != null && !body.isEmpty())
                {
                    // Update cache and refresh overlays
                    chunkpickerUnlocked = parseChunkpickerKeys(body);
                    updateForbiddenRegionsFromLoadedRegions();
                }
            }
            lastChunkpickerFetchMs = now;
        }

	int regionId = worldPoint.getRegionID();
	List<String> unlocked = resolveUnlockedRegions();
        boolean hasList = unlocked != null && !unlocked.isEmpty();
        boolean isUnlocked = !hasList || unlocked.contains(Integer.toString(regionId));
        if (!isWithinMainland(worldPoint))
        {
            // Outside mainland bounds is always permitted
            isUnlocked = true;
        }

		// Movement-based shading removed; handled on GameStateChanged via loaded regions

		if (lastRegionId == null || !lastRegionId.equals(regionId))
		{
            if (config.debugText())
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "regionId=" + regionId + " unlocked=" + isUnlocked, null);
            }
			lastRegionId = regionId;
		}

		if (!isUnlocked)
		{
			if (!inLockedRegion)
			{
				inLockedRegion = true;
				lockedEnterMs = now;
				lastRemainingSec = -1;
				lastGrueMs = 0L;
			}

			long elapsedSec = Math.max(0L, (now - lockedEnterMs) / 1000L);
			int remainingSec = (int) Math.max(0L, 5L - elapsedSec);
			// Update blackout fade level each tick: 0s -> 255 alpha, 5s -> ~51 alpha
            int alpha = (int)Math.max(0, Math.min(255, (5 - remainingSec) * (255 / 5.0)));
            if (config.screenFade() && !config.opaqueAfterFive())
            {
                int cap = (int)(255 * 0.8);
                if (alpha > cap)
                {
                    alpha = cap;
                }
            }
            if (config.screenFade())
            {
                if (lastRemainingSec == -1 && remainingSec > 0)
                {
                    // First entry into locked region: add overlay and start faded
                    overlayManager.add(blackoutOverlay);
                }
                blackoutOverlay.setState(alpha, false);
            }
			if (remainingSec != lastRemainingSec)
			{
				int volume;
				switch (remainingSec)
				{
					case 5:
						volume = SoundEffectVolume.LOW;
						break;
					case 4:
						volume = SoundEffectVolume.MEDIUM_LOW;
						break;
					case 3:
						volume = SoundEffectVolume.MEDIUM_HIGH;
						break;
					default: // 2, 1, 0
						volume = SoundEffectVolume.HIGH;
				}
                if (remainingSec > 0 && config.fadeWarningSound())
                {
                    client.playSoundEffect(3482, volume);
                }
                if (config.screenFade() || config.fadeWarningSound())
                {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You arent supposed to be here, you will perish in " + remainingSec + " seconds.", null);
                }
				// Crossed a boundary; update last seen
				lastRemainingSec = remainingSec;
			}

            // While at 0 or below, handle overlay and grue warning
			if (remainingSec <= 0)
			{
                if (config.screenFade())
                {
                    overlayManager.add(blackoutOverlay);
                    int finalAlpha = config.opaqueAfterFive() ? 255 : (int)(255 * 0.8);
                    blackoutOverlay.setState(finalAlpha, config.opaqueAfterFive());
                }
                if (config.fadeWarningSound() && (now - lastGrueMs >= 200L))
				{
					client.playSoundEffect(3485, SoundEffectVolume.HIGH);
                    if (config.screenFade() || config.fadeWarningSound())
                    {
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You have been eaten by a grue.", null);
                    }
					lastGrueMs = now;
				}
                else if ((now - lastGrueMs >= 200L))
                {
                    // Keep lastGrueMs advancing to avoid rapid loop if sound disabled
                    lastGrueMs = now;
                }
			}
		}
		else if (inLockedRegion)
		{
			long durationSec = Math.max(0L, (now - lockedEnterMs) / 1000L);
            if (config.debugText())
            {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Time in locked region: " + durationSec + "s", null);
            }
			inLockedRegion = false;
			lastRemainingSec = -1;
			overlayManager.remove(blackoutOverlay);
			lastGrueMs = 0L;
		}
	}

	@Provides
    LockedChunkOverlayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LockedChunkOverlayConfig.class);
	}
}
