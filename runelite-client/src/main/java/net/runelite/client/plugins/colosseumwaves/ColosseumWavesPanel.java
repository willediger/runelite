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

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColosseumWavesPanel extends PluginPanel
{
	private final ColosseumWavesPlugin plugin;
	private final JPanel wavesContainer;
	private final List<WavePanel> wavePanels = new ArrayList<>();

	private static final Color bgColor = ColorScheme.DARK_GRAY_COLOR;
	private static final Color hoverColor = new Color(52, 52, 52);
	private static final Color buttonColor = ColorScheme.DARKER_GRAY_COLOR;

	@Inject
	public ColosseumWavesPanel(ColosseumWavesPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setBackground(bgColor);
		setLayout(new BorderLayout());

		// Create header panel with "Current LoS" button and "Waves" label
		JPanel headerPanel = new JPanel();
		headerPanel.setBackground(bgColor);
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Current LoS button
		JButton currentLoSButton = new JButton("Current LoS");
		currentLoSButton.setFocusPainted(false);
		currentLoSButton.setBackground(buttonColor);
		currentLoSButton.setForeground(Color.WHITE);
		currentLoSButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		currentLoSButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		currentLoSButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		currentLoSButton.setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
		currentLoSButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		currentLoSButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				currentLoSButton.setBackground(hoverColor);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				currentLoSButton.setBackground(buttonColor);
			}
		});

		currentLoSButton.addActionListener(e ->
		{
			String currentUrl = plugin.generateCurrentLoSLink();
			if (currentUrl != null)
			{
				openWebpage(currentUrl);
			}
		});

		// Waves label (styled as a button but non-functional)
		JLabel wavesLabel = new JLabel("Waves");
		wavesLabel.setOpaque(true);
		wavesLabel.setBackground(buttonColor);
		wavesLabel.setForeground(Color.WHITE);
		wavesLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		wavesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		wavesLabel.setHorizontalAlignment(SwingConstants.CENTER);
		wavesLabel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
		wavesLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		headerPanel.add(currentLoSButton);
		headerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		headerPanel.add(wavesLabel);

		add(headerPanel, BorderLayout.NORTH);

		// Create scrollable container for waves
		wavesContainer = new JPanel();
		wavesContainer.setLayout(new BoxLayout(wavesContainer, BoxLayout.Y_AXIS));
		wavesContainer.setBackground(bgColor);

		JPanel scrollWrapper = new JPanel(new BorderLayout());
		scrollWrapper.setBackground(bgColor);
		scrollWrapper.add(wavesContainer, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(scrollWrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		scrollPane.setBackground(bgColor);
		scrollPane.getViewport().setBackground(bgColor);
		scrollPane.setBorder(null);

		add(scrollPane, BorderLayout.CENTER);
	}

	private void openWebpage(String url)
	{
		try
		{
			Desktop.getDesktop().browse(new URI(url));
		}
		catch (Exception ex)
		{
			log.debug("[DEBUG] Error opening URL: {}", ex);
		}
	}

	public void addWave(int waveNumber)
	{
		SwingUtilities.invokeLater(() ->
		{
			WavePanel wavePanel = new WavePanel(waveNumber);
			wavePanels.add(wavePanel);
			wavesContainer.add(wavePanel);
			wavesContainer.add(Box.createRigidArea(new Dimension(0, 5)));

			wavesContainer.revalidate();
			this.revalidate();
		});
	}

	public void setWaveSpawnUrl(int waveNumber, String url)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (waveNumber > 0 && waveNumber <= wavePanels.size())
			{
				wavePanels.get(waveNumber - 1).setSpawnUrl(url);
			}
		});
	}

	public void setWaveReinforcementUrl(int waveNumber, String url)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (waveNumber > 0 && waveNumber <= wavePanels.size())
			{
				wavePanels.get(waveNumber - 1).setReinforcementUrl(url);
			}
		});
	}

	public void reset()
	{
		SwingUtilities.invokeLater(() ->
		{
			wavePanels.clear();
			wavesContainer.removeAll();
			wavesContainer.revalidate();
		});
	}

	private static class WavePanel extends JPanel
	{
		private final JLabel waveNumberLabel;
		private final JButton spawnButton;
		private JButton reinforcementButton;
		private final JPanel placeholderPanel;

		public WavePanel(int waveNumber)
		{
			// Desired layout:  [number] [Spawn] [Reinforcements]
			setLayout(new BorderLayout());
			setBackground(bgColor);
			setBorder(null); // No outline around each wave row

			JPanel row = new JPanel(new GridLayout(1, 3, 5, 0));
			row.setBackground(getBackground());

			// # label
			waveNumberLabel = new JLabel(String.valueOf(waveNumber), SwingConstants.CENTER);
			waveNumberLabel.setOpaque(true);
			waveNumberLabel.setBackground(buttonColor);
			waveNumberLabel.setForeground(Color.WHITE);
			waveNumberLabel.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
			row.add(waveNumberLabel);

			// Spawn button
			spawnButton = createLinkButton("Spawn");
			row.add(spawnButton);

			// Placeholder (keeps 3rd column width until reinforcements arrive)
			placeholderPanel = new JPanel();
			placeholderPanel.setOpaque(false);
			row.add(placeholderPanel);

			add(row, BorderLayout.CENTER);
		}

		private JButton createLinkButton(String text)
		{
			JButton button = new JButton(text);
			button.setFocusPainted(false);
			button.setBackground(buttonColor);
			button.setForeground(Color.GRAY);
			button.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
			button.setEnabled(false);
			button.setCursor(new Cursor(Cursor.HAND_CURSOR));

			button.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					if (button.isEnabled())
					{
						button.setBackground(hoverColor);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					button.setBackground(buttonColor);
				}
			});

			return button;
		}

		public void setSpawnUrl(String url)
		{
			spawnButton.setEnabled(true);
			spawnButton.setForeground(Color.WHITE);
			for (var listener : spawnButton.getActionListeners())
			{
				spawnButton.removeActionListener(listener);
			}
			spawnButton.addActionListener(e -> openUrl(url));
		}

		public void setReinforcementUrl(String url)
		{
			if (reinforcementButton == null)
			{
				Container parent = placeholderPanel.getParent();
				parent.remove(placeholderPanel);

				reinforcementButton = createLinkButton("Reinforcements");
				parent.add(reinforcementButton);
				parent.revalidate();
			}

			reinforcementButton.setEnabled(true);
			reinforcementButton.setForeground(Color.WHITE);
			for (var listener : reinforcementButton.getActionListeners())
			{
				reinforcementButton.removeActionListener(listener);
			}
			reinforcementButton.addActionListener(e -> openUrl(url));
		}

		private void openUrl(String url)
		{
			try
			{
				Desktop.getDesktop().browse(new URI(url));
			}
			catch (Exception ex)
			{
				log.warn("Unable to open URL {}", url, ex);
			}
		}
	}
}
