/*
 * Copyright (c) 2025, Will Ediger <will.ediger@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.colosseumwaves;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.NPC;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.apache.commons.lang3.ArrayUtils;

@PluginDescriptor(
	name = "Colosseum Waves",
	description = "Capture Fortis Colosseum wave spawns and pillar stacks and generate shareable links to line of sight tool for analysis",
	tags = {"fortis", "colosseum", "waves", "wave", "spawns", "los"},
	configName = "colosseumwaves"
)
@Slf4j
public class ColosseumWavesPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ColosseumWavesConfig config;

	private ColosseumWavesPanel panel;
	private NavigationButton navButton;

	private ManticoreHandler manticoreHandler;

	// Fortis Colosseum region ID
	private static final int COLOSSEUM_REGION_ID = 7216;

	// NPCs to exclude from tracking
	private static final Set<String> EXCLUDED_NPC_NAMES = ImmutableSet.of(
		"Minimus",
		"Sol Heredit",
		"Fremennik warband berserker",
		"Fremennik warband archer",
		"Fremennik warband seer"
	);

	// Colosseum wave NPCs with their LoS tool ID mapping
	private static final Map<Integer, Integer> COLOSSEUM_WAVE_NPCS =
		ImmutableMap.<Integer, Integer>builder()
			.put(12811, 1) // Serpent shaman
			.put(12817, 2) // Javelin Colossus
			.put(12810, 3) // Jaguar warrior
			.put(12818, 4) // Manticore
			.put(12812, 5) // Minotaur
			.put(12813, 5) // Minotaur (Red Flag)
			.put(12819, 6) // Shockwave Colossus
			.build();

	// Pattern to match "Wave: X" messages
	private static final Pattern WAVE_PATTERN = Pattern.compile("Wave: (\\d+)");

	// Timing constants for wave spawn detection
	private static final int INITIAL_SPAWN_WINDOW_TICKS = 20;  // NPCs spawn within 20 ticks of wave start
	private static final int REINFORCEMENT_THRESHOLD_TICKS = 50; // After 50 ticks, spawns are reinforcements

	// Colosseum scene bounds
	private static final int COLOSSEUM_MIN_SCENE_X = 35;
	private static final int COLOSSEUM_MAX_SCENE_X = 62;
	private static final int COLOSSEUM_MIN_SCENE_Y = 53;
	private static final int COLOSSEUM_MAX_SCENE_Y = 82;

	// LoS tool coordinate conversion
	private static final int LOS_COORD_OFFSET_X = 32;
	private static final int LOS_COORD_OFFSET_Y = 83;
	private static final int LOS_COORD_MAX = 33;

	// State tracking
	private boolean inColosseum = false;
	private int currentWave = 0;
	private boolean waveComplete = false;
	private boolean isReinforcementWave = false;
	private boolean expectingWaveSpawn = false;

	// Timing
	private int waveStartTick = 0;
	private int lastWaveSpawnTick = 0;

	// NPC tracking
	private final Map<NPC, Point> npcLastPositions = new HashMap<>();
	private final Map<NPC, NPCSpawn> activeNPCs = new HashMap<>();
	private final List<NPCSpawn> waveSpawns = new ArrayList<>();

	// Player tracking
	private Point currentPlayerLoSLocation = null;

	// Enum for different LoS link contexts
	private enum LoSContext
	{
		SPAWN,
		REINFORCEMENTS,
		CURRENT
	}

	// Helper class to store NPC spawn info
	@Value
	private static class NPCSpawn
	{
		int npcId;
		Point location;
		String name;
	}

	@Provides
	ColosseumWavesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ColosseumWavesConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		resetState();

		panel = injector.getInstance(ColosseumWavesPanel.class);
		manticoreHandler = new ManticoreHandler(client, this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "colosseum_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Colosseum Waves")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (manticoreHandler != null)
		{
			manticoreHandler.clear();
		}

		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;

		resetState();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		Matcher matcher = WAVE_PATTERN.matcher(message);

		if (matcher.find())
		{
			int newWave = Integer.parseInt(matcher.group(1));

			// Reset panel ONLY when starting Wave 1 (new run)
			if (newWave == 1 && panel != null)
			{
				panel.reset();
			}

			currentWave = newWave;
			waveStartTick = client.getTickCount();
			expectingWaveSpawn = true;

			waveSpawns.clear();
			activeNPCs.clear();
			waveComplete = false;
			isReinforcementWave = false;
			log.debug("wave: {}, tick: {}", newWave, waveStartTick);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (inColosseum && event.getGameState() == GameState.LOGGED_IN)
		{
			if (!isInColosseum())
			{
				resetState();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Check if we entered/left the Colosseum
		if (!inColosseum && isInColosseum())
		{
			inColosseum = true;
		}
		else if (inColosseum && !isInColosseum())
		{
			resetState();
		}

		// Track player position for LoS coordinates
		if (inColosseum)
		{
			updatePlayerLoSLocation(localPlayer);

			// Track NPC movements and check manticore graphics
			trackNPCMovements();
			checkManticoreGraphics();

			// Handle wave spawns or reinforcements and update panel
			if (waveComplete && !waveSpawns.isEmpty() &&
				client.getTickCount() - lastWaveSpawnTick >= 0)
			{
				handleWaveSpawnsAndReinforcements();
				waveComplete = false;
			}
		}
	}

	private void updatePlayerLoSLocation(Player localPlayer)
	{
		WorldPoint worldLocation = localPlayer.getWorldLocation();
		LocalPoint localPoint = LocalPoint.fromWorld(client, worldLocation);

		if (localPoint != null)
		{
			Scene scene = client.getScene();
			Tile[][][] tiles = scene.getTiles();

			int sceneX = localPoint.getSceneX();
			int sceneY = localPoint.getSceneY();
			int plane = client.getPlane();

			if (sceneX >= 0 && sceneX < 104 && sceneY >= 0 && sceneY < 104 && plane >= 0 && plane < 4)
			{
				Tile playerTile = tiles[plane][sceneX][sceneY];
				if (playerTile != null)
				{
					Point sceneLocation = playerTile.getSceneLocation();
					currentPlayerLoSLocation = convertToLoSCoordinates(sceneLocation);
				}
			}
		}
	}

	private boolean isInColosseum()
	{
		return ArrayUtils.contains(client.getMapRegions(), COLOSSEUM_REGION_ID);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!inColosseum)
		{
			return;
		}

		NPC npc = event.getNpc();

		// Track manticores
		if (manticoreHandler != null)
		{
			manticoreHandler.onNpcSpawned(npc);
		}

		// Skip excluded NPCs
		if (npc.getName() != null && EXCLUDED_NPC_NAMES.contains(npc.getName()))
		{
			return;
		}

		Point npcLocation = getNPCSceneLocation(npc);

		if (npcLocation != null)
		{
			npcLastPositions.put(npc, npcLocation);

			// Store spawn position for LoS tool generation
			if (COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				int currentTick = client.getTickCount();
				determineSpawnType(currentTick);
				lastWaveSpawnTick = currentTick;

				NPCSpawn spawn = new NPCSpawn(npc.getId(), npcLocation, npc.getName());
				activeNPCs.put(npc, spawn);

				if (!isReinforcementWave)
				{
					waveSpawns.add(spawn);
					log.debug("Added {} to wave spawns at position {}", npc.getName(), npcLocation);
				}

				if (isReinforcementWave)
				{
					captureAllNPCPositions();
					log.debug("Captured all NPC positions for reinforcement wave");
				}

				if (!waveComplete)
				{
					waveComplete = true;
				}
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!inColosseum)
		{
			return;
		}

		NPC npc = event.getNpc();

		// Track manticore despawn
		if (manticoreHandler != null)
		{
			manticoreHandler.onNpcDespawned(npc);
		}

		npcLastPositions.remove(npc);
		activeNPCs.remove(npc);
	}

	private void trackNPCMovements()
	{
		Map<NPC, Point> currentPositions = new HashMap<>(npcLastPositions);

		for (Map.Entry<NPC, Point> entry : currentPositions.entrySet())
		{
			NPC npc = entry.getKey();
			if (npc == null || npc.getName() == null)
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
				continue;
			}

			if (EXCLUDED_NPC_NAMES.contains(npc.getName()))
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
				continue;
			}

			Point lastPos = entry.getValue();
			Point currentPos = getNPCSceneLocation(npc);

			if (currentPos == null)
			{
				npcLastPositions.remove(npc);
				activeNPCs.remove(npc);
			}
			else if (!currentPos.equals(lastPos))
			{
				npcLastPositions.put(npc, currentPos);

				if (activeNPCs.containsKey(npc))
				{
					NPCSpawn oldSpawn = activeNPCs.get(npc);
					activeNPCs.put(npc, new NPCSpawn(oldSpawn.npcId, currentPos, oldSpawn.name));
				}
			}
		}
	}

	private void checkManticoreGraphics()
	{
		if (!inColosseum || manticoreHandler == null)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getId() == 12818) // Manticore
			{
				manticoreHandler.checkNPCGraphics(npc);
			}
		}
	}

	private void captureAllNPCPositions()
	{
		waveSpawns.clear();

		for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
		{
			NPC npc = entry.getKey();
			if (npc != null && COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				Point currentPos = getNPCSceneLocation(npc);
				if (currentPos != null)
				{
					waveSpawns.add(new NPCSpawn(npc.getId(), currentPos, npc.getName()));
				}
			}
		}
	}

	private Point getNPCSceneLocation(NPC npc)
	{
		WorldPoint worldPoint = npc.getWorldLocation();
		LocalPoint worldLocalPoint = LocalPoint.fromWorld(client, worldPoint);

		if (worldLocalPoint == null)
		{
			return null;
		}

		int sceneX = worldLocalPoint.getSceneX();
		int sceneY = worldLocalPoint.getSceneY();

		// Check if the NPC is within the colosseum bounds
		if (!isWithinColosseumBounds(sceneX, sceneY))
		{
			return null;
		}

		return new Point(sceneX, sceneY);
	}

	/**
	 * Checks if the given scene coordinates are within the Colosseum arena bounds.
	 */
	private boolean isWithinColosseumBounds(int sceneX, int sceneY)
	{
		return sceneX >= COLOSSEUM_MIN_SCENE_X && sceneX <= COLOSSEUM_MAX_SCENE_X
			&& sceneY >= COLOSSEUM_MIN_SCENE_Y && sceneY <= COLOSSEUM_MAX_SCENE_Y;
	}

	/**
	 * Converts scene coordinates to LoS tool coordinates.
	 * The LoS tool uses a different coordinate system with (0,0) at the top-left.
	 */
	private Point convertToLoSCoordinates(Point sceneLocation)
	{
		int losX = sceneLocation.getX() - LOS_COORD_OFFSET_X;
		int losY = LOS_COORD_OFFSET_Y - sceneLocation.getY();

		// Clamp coordinates to valid LoS tool range
		losX = Math.max(0, Math.min(LOS_COORD_MAX, losX));
		losY = Math.max(0, Math.min(LOS_COORD_MAX, losY));

		return new Point(losX, losY);
	}

	/**
	 * Handles wave spawns (initial NPCs) and reinforcements (NPCs that spawn ~40s later).
	 * Generates LoS links and updates the panel accordingly.
	 * Called automatically after NPCs have finished spawning.
	 */
	private void handleWaveSpawnsAndReinforcements()
	{
		if (waveSpawns.isEmpty() || panel == null || currentWave <= 0)
		{
			return;
		}

		// Generate the LoS URL for the current spawn configuration
		LoSContext context = isReinforcementWave ? LoSContext.REINFORCEMENTS : LoSContext.SPAWN;
		String url = buildLoSUrl(waveSpawns, context);

		// Update the panel based on whether these are initial spawns or reinforcements
		if (isReinforcementWave)
		{
			panel.setWaveReinforcementUrl(currentWave, url);
		}
		else
		{
			panel.addWave(currentWave);
			panel.setWaveSpawnUrl(currentWave, url);
		}

		// Reset reinforcement flag after processing
		if (isReinforcementWave)
		{
			isReinforcementWave = false;
		}
	}

	/**
	 * Generates a LoS link for the current NPC positions in the colosseum.
	 * Used by the "Current LoS" button to capture the current state.
	 *
	 * @return the LoS URL for current NPC positions, or null if not in colosseum or no NPCs
	 */
	public String generateCurrentLoSLink()
	{
		if (!inColosseum)
		{
			return null;
		}

		List<NPCSpawn> currentSpawns = collectCurrentNPCSpawns();
		if (currentSpawns.isEmpty())
		{
			return null;
		}

		return buildLoSUrl(currentSpawns, LoSContext.CURRENT);
	}

	/**
	 * Collects the current positions of all tracked NPCs in the colosseum.
	 *
	 * @return a list of current NPC spawn positions
	 */
	private List<NPCSpawn> collectCurrentNPCSpawns()
	{
		return activeNPCs.values().stream()
			.filter(spawn -> spawn != null && COLOSSEUM_WAVE_NPCS.containsKey(spawn.npcId))
			.collect(Collectors.toList());
	}

	/**
	 * Determines if the current NPC spawn is part of the initial wave or a reinforcement.
	 * Initial wave spawns occur within INITIAL_SPAWN_WINDOW_TICKS of the wave start.
	 * Reinforcements spawn after REINFORCEMENT_THRESHOLD_TICKS.
	 */
	private void determineSpawnType(int currentTick)
	{
		if (waveStartTick <= 0)
		{
			return;
		}

		int ticksSinceWaveStart = currentTick - waveStartTick;

		// First spawn within the initial window - this is a wave spawn
		if (expectingWaveSpawn && ticksSinceWaveStart < INITIAL_SPAWN_WINDOW_TICKS)
		{
			expectingWaveSpawn = false;
			isReinforcementWave = false;
			log.debug("Initial wave spawn detected at {} ticks after wave start", ticksSinceWaveStart);
		}
		// Spawns after the reinforcement threshold are reinforcements
		else if (ticksSinceWaveStart > REINFORCEMENT_THRESHOLD_TICKS && !isReinforcementWave)
		{
			isReinforcementWave = true;
			log.debug("Reinforcement spawn detected at {} ticks after wave start", ticksSinceWaveStart);
		}
	}

	/**
	 * Determines whether to include player position in the LoS link based on
	 * the current context and config settings.
	 *
	 * @param context the context for which the LoS link is being generated
	 */
	private boolean shouldIncludePlayerPosition(LoSContext context)
	{
		switch (context)
		{
			case SPAWN:
				return config.includePlayerLocationSpawns();
			case REINFORCEMENTS:
				return config.includePlayerLocationReinforcements();
			case CURRENT:
				return config.includePlayerLocationCurrent();
			default:
				return false;
		}
	}

	/**
	 * Appends the player position to the URL if configured and available.
	 * Player position is encoded as a single integer: x + (256 * y)
	 *
	 * @param urlBuilder the StringBuilder to append to
	 * @param context    the context for which the LoS link is being generated
	 */
	private void appendPlayerPositionIfNeeded(StringBuilder urlBuilder, LoSContext context)
	{
		if (shouldIncludePlayerPosition(context) && currentPlayerLoSLocation != null)
		{
			int playerEncoded = currentPlayerLoSLocation.getX() + (256 * currentPlayerLoSLocation.getY());
			urlBuilder.append("#").append(playerEncoded);
		}
	}

	/**
	 * Appends the appropriate suffix for manticore NPCs based on their attack pattern.
	 * Manticores need a suffix to indicate whether they start with magic or ranged attacks.
	 */
	private void appendManticoreSuffixIfNeeded(StringBuilder urlBuilder, NPCSpawn spawn)
	{
		if (spawn.npcId != 12818) // Not a manticore
		{
			return;
		}

		char suffix = 'm'; // Default to mage-first

		// Find the corresponding NPC to get its attack pattern
		for (Map.Entry<NPC, NPCSpawn> entry : activeNPCs.entrySet())
		{
			NPCSpawn activeSpawn = entry.getValue();
			if (activeSpawn.npcId == spawn.npcId &&
				activeSpawn.location.equals(spawn.location) &&
				manticoreHandler != null)
			{
				suffix = manticoreHandler.getManticoreLosSuffix(entry.getKey());
				break;
			}
		}

		urlBuilder.append(suffix);
	}

	/**
	 * Builds a LoS tool URL from a list of NPC spawns.
	 *
	 * @param spawns  the list of NPC spawns to encode
	 * @param context the context for which the URL is being generated
	 * @return the complete LoS tool URL
	 */
	private String buildLoSUrl(List<NPCSpawn> spawns, LoSContext context)
	{
		StringBuilder urlBuilder = new StringBuilder("https://los.colosim.com/?");

		for (NPCSpawn spawn : spawns)
		{
			Point losPos = convertToLoSCoordinates(spawn.location);
			Integer losNpcId = COLOSSEUM_WAVE_NPCS.get(spawn.npcId);

			if (losNpcId != null)
			{
				String spawnCode = String.format("%02d%02d%d", losPos.getX(), losPos.getY(), losNpcId);
				urlBuilder.append(spawnCode);

				appendManticoreSuffixIfNeeded(urlBuilder, spawn);
				urlBuilder.append(".");
			}
		}

		// Add player position based on context and config
		appendPlayerPositionIfNeeded(urlBuilder, context);

		return urlBuilder.toString();
	}

	private void resetState()
	{
		currentPlayerLoSLocation = null;
		npcLastPositions.clear();
		waveSpawns.clear();
		activeNPCs.clear();
		inColosseum = false;
		currentWave = 0;
		isReinforcementWave = false;
		expectingWaveSpawn = false;
		waveStartTick = 0;
		lastWaveSpawnTick = 0;
	}
}