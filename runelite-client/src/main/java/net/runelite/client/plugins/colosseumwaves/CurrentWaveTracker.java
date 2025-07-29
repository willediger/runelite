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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Point;

@Getter
public class CurrentWaveTracker
{
	private final List<NpcSpawn> waveSpawns = new ArrayList<>();
	private final List<NpcSpawn> reinforcementSpawns = new ArrayList<>();
	@Setter
	private Point playerLocationAtWaveSpawn;
	@Setter
	private Point playerLocationAtReinforcements;

	public void reset()
	{
		waveSpawns.clear();
		reinforcementSpawns.clear();
		playerLocationAtWaveSpawn = null;
		playerLocationAtReinforcements = null;
	}

	public void recordInitialSpawns(List<NpcSpawn> spawns)
	{
		waveSpawns.clear();
		waveSpawns.addAll(spawns);
	}

	public void recordReinforcementSpawns(List<NpcSpawn> spawns)
	{
		reinforcementSpawns.clear();
		reinforcementSpawns.addAll(spawns);
	}

	public boolean hasWaveSpawns()
	{
		return !waveSpawns.isEmpty();
	}

	public boolean hasReinforcements()
	{
		return !reinforcementSpawns.isEmpty();
	}
}