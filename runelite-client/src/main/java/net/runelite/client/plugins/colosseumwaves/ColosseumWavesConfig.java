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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("colosseumwaves")
public interface ColosseumWavesConfig extends Config
{
	@ConfigSection(
		name = "Include Player Location",
		description = "Configure when to include player location in LoS links",
		position = 0
	)
	String includePlayerLocationSection = "includePlayerLocation";

	@ConfigItem(
		keyName = "includePlayerLocationSpawns",
		name = "Spawn",
		description = "Include player location in LoS links for wave spawns",
		position = 1,
		section = includePlayerLocationSection
	)
	default boolean includePlayerLocationSpawns()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includePlayerLocationReinforcements",
		name = "Reinforcements",
		description = "Include player location in LoS links for reinforcements",
		position = 2,
		section = includePlayerLocationSection
	)
	default boolean includePlayerLocationReinforcements()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includePlayerLocationCurrent",
		name = "Current LoS",
		description = "Include player location in LoS links for the Current LoS button",
		position = 3,
		section = includePlayerLocationSection
	)
	default boolean includePlayerLocationCurrent()
	{
		return true;
	}
}