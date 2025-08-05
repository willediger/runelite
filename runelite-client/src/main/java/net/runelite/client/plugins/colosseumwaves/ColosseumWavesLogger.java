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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Point;
import net.runelite.client.RuneLite;

@Singleton
public class ColosseumWavesLogger
{
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

	private BufferedWriter writer;
	private File logFile;
	private final Set<String> loggedOncePerWave = new HashSet<>();
	private int currentWave = 0;

	public void startNewSession()
	{
		closeLog();

		String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
		String fileName = String.format("colosseum_waves_%s.log", timestamp);
		logFile = new File(RuneLite.LOGS_DIR, fileName);

		try
		{
			writer = new BufferedWriter(new FileWriter(logFile, true));
			log("========================================");
			log("Colosseum Waves Plugin Session Started");
			log("========================================");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void closeLog()
	{
		if (writer != null)
		{
			try
			{
				log("Session ended");
				writer.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			writer = null;
		}
	}

	private void log(String message)
	{
		if (writer == null)
		{
			return;
		}

		try
		{
			String timestamp = LocalDateTime.now().format(DATE_FORMAT);
			writer.write(String.format("[%s] %s%n", timestamp, message));
			writer.flush();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void logOncePerWave(String key, String message)
	{
		String waveKey = currentWave + ":" + key;
		if (!loggedOncePerWave.contains(waveKey))
		{
			log(message);
			loggedOncePerWave.add(waveKey);
		}
	}

	public void logWaveStart(int wave)
	{
		currentWave = wave;
		loggedOncePerWave.clear();
		log("");
		log("================== WAVE " + wave + " START ==================");
	}

	public void logWaveComplete(int wave, String duration)
	{
		log("Wave " + wave + " completed. Duration: " + duration);
		log("================== WAVE " + wave + " END ====================");
	}

	public void logNpcSpawn(String phase, int npcId, int npcIndex, Point scenePos, Point losPos)
	{
		log(String.format("%s spawn: NPC ID=%d, Index=%d, Scene=(%d,%d), LoS=(%d,%d)",
			phase, npcId, npcIndex,
			scenePos.getX(), scenePos.getY(),
			losPos.getX(), losPos.getY()));
	}

	public void logManticoreSpawn(int index, Point scenePos, Point losPos)
	{
		log(String.format("MANTICORE spawn: Index=%d, Scene=(%d,%d), LoS=(%d,%d)",
			index, scenePos.getX(), scenePos.getY(), losPos.getX(), losPos.getY()));
	}

	public void logManticoreOrbDetected(int index, String orbType, List<String> currentOrder)
	{
		log(String.format("Manticore %d: New orb detected: %s, Order: %s",
			index, orbType, currentOrder));
	}
	
	public void logManticoreGraphicEvent(int index, int graphicId)
	{
		log(String.format("Manticore %d: Graphics event received, ID: %d", index, graphicId));
	}

	public void logManticoreFullyCharged(int index, List<String> finalOrder)
	{
		logOncePerWave("manticore_charged_" + index,
			String.format("Manticore %d: FULLY CHARGED with order: %s", index, finalOrder));
	}

	public void logManticoreAlreadyCharged(int index)
	{
		logOncePerWave("manticore_already_" + index,
			String.format("Manticore %d: Already fully charged, skipping orb tracking", index));
	}

	public void logReinforcementsPhase()
	{
		log("");
		log(">>> REINFORCEMENTS PHASE <<<");
	}

	public void logManticoreStateAtReinforcements(int index, boolean hadOrbs, List<String> orbOrder)
	{
		log(String.format("Manticore %d at reinforcements: Had orbs=%s, Order=%s",
			index, hadOrbs, orbOrder));
	}

	public void logUrlGenerated(String type, int wave, String url)
	{
		log(String.format("URL generated - Wave %d %s: %s", wave, type, url));
	}

	public void logUrlUpdate(String type, int wave, String url, String reason)
	{
		log(String.format("URL updated - Wave %d %s (%s): %s", wave, type, reason, url));
	}

	public void logManticoreSuffix(int index, String phase, String suffix)
	{
		log(String.format("Manticore %d suffix for %s: '%s'", index, phase, suffix));
	}

	public void logPlayerLocation(String phase, Point losPos)
	{
		if (losPos != null)
		{
			log(String.format("Player location at %s: LoS=(%d,%d)",
				phase, losPos.getX(), losPos.getY()));
		}
	}

	public void logButtonAction(String action)
	{
		log("Button action: " + action);
	}

	public void logDebug(String message)
	{
		log("[DEBUG] " + message);
	}

	public void logError(String message)
	{
		log("[ERROR] " + message);
	}

	public void logColosseumEntered()
	{
		log("");
		log(">>> ENTERED COLOSSEUM <<<");
	}

	public void logColosseumExited()
	{
		log(">>> EXITED COLOSSEUM <<<");
		log("");
	}

	public void logMantimayhem3Status(boolean active)
	{
		log("Mantimayhem III status: " + (active ? "ACTIVE" : "INACTIVE"));
	}
}