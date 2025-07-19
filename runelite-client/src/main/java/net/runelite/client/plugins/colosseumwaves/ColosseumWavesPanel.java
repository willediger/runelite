package net.runelite.client.plugins.colosseumwaves;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ColosseumWavesPanel extends PluginPanel
{
    private final ColosseumWavesPlugin plugin;
    private final JPanel wavesContainer;
    private final List<WavePanel> wavePanels = new ArrayList<>();

    @Inject
    public ColosseumWavesPanel(ColosseumWavesPlugin plugin)
    {
        this.plugin = plugin;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create header panel with "Current LoS" button
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton currentLoSButton = new JButton("Current LoS");
        currentLoSButton.setFocusPainted(false);
        currentLoSButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        currentLoSButton.setForeground(Color.WHITE);
        currentLoSButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        currentLoSButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        currentLoSButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                currentLoSButton.setBackground(ColorScheme.DARK_GRAY_COLOR.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                currentLoSButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });

        currentLoSButton.addActionListener(e -> {
            // Get the current LoS link from the plugin
            String currentUrl = plugin.generateCurrentLoSLink();
            if (currentUrl != null) {
                openWebpage(currentUrl);
            }
        });

        headerPanel.add(currentLoSButton, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Create scrollable container for waves
        wavesContainer = new JPanel();
        wavesContainer.setLayout(new BoxLayout(wavesContainer, BoxLayout.Y_AXIS));
        wavesContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(wavesContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void openWebpage(String url)
    {
        try
        {
            System.out.println("[DEBUG] Opening URL: " + url);
            Desktop.getDesktop().browse(new URI(url));
            System.out.println("[DEBUG] URL opened successfully");
        }
        catch (Exception ex)
        {
            System.out.println("[DEBUG] Error opening URL: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void addWave(int waveNumber)
    {
        System.out.println("[DEBUG ColosseumWavesPanel] addWave called for wave " + waveNumber);
        SwingUtilities.invokeLater(() -> {
            System.out.println("[DEBUG ColosseumWavesPanel] addWave executing in Swing thread for wave " + waveNumber);
            WavePanel wavePanel = new WavePanel(waveNumber);
            wavePanels.add(wavePanel);
            wavesContainer.add(wavePanel);
            wavesContainer.add(Box.createRigidArea(new Dimension(0, 5)));

            // Force immediate update
            wavesContainer.revalidate();
            wavesContainer.repaint();

            // Also try updating the parent components
            this.revalidate();
            this.repaint();

            // Force layout
            wavesContainer.doLayout();

            System.out.println("[DEBUG ColosseumWavesPanel] Wave " + waveNumber + " panel added successfully");
            System.out.println("[DEBUG ColosseumWavesPanel] Container has " + wavesContainer.getComponentCount() + " components");
            System.out.println("[DEBUG ColosseumWavesPanel] Panel visible: " + wavePanel.isVisible());
        });
    }

    public void setWaveSpawnUrl(int waveNumber, String url)
    {
        System.out.println("[DEBUG ColosseumWavesPanel] setWaveSpawnUrl called for wave " + waveNumber + " with URL: " + url);
        SwingUtilities.invokeLater(() -> {
            System.out.println("[DEBUG ColosseumWavesPanel] setWaveSpawnUrl executing in Swing thread for wave " + waveNumber);
            if (waveNumber > 0 && waveNumber <= wavePanels.size())
            {
                wavePanels.get(waveNumber - 1).setSpawnUrl(url);
                System.out.println("[DEBUG ColosseumWavesPanel] Wave " + waveNumber + " spawn URL set successfully");
            }
            else
            {
                System.out.println("[DEBUG ColosseumWavesPanel] ERROR: Wave " + waveNumber + " not found! wavePanels.size() = " + wavePanels.size());
            }
        });
    }

    public void setWaveReinforcementUrl(int waveNumber, String url)
    {
        System.out.println("[DEBUG ColosseumWavesPanel] setWaveReinforcementUrl called for wave " + waveNumber + " with URL: " + url);
        SwingUtilities.invokeLater(() -> {
            System.out.println("[DEBUG ColosseumWavesPanel] setWaveReinforcementUrl executing in Swing thread for wave " + waveNumber);
            if (waveNumber > 0 && waveNumber <= wavePanels.size())
            {
                wavePanels.get(waveNumber - 1).setReinforcementUrl(url);
                System.out.println("[DEBUG ColosseumWavesPanel] Wave " + waveNumber + " reinforcement URL set successfully");
            }
            else
            {
                System.out.println("[DEBUG ColosseumWavesPanel] ERROR: Wave " + waveNumber + " not found! wavePanels.size() = " + wavePanels.size());
            }
        });
    }

    public void reset()
    {
        SwingUtilities.invokeLater(() -> {
            wavePanels.clear();
            wavesContainer.removeAll();
            wavesContainer.revalidate();
            wavesContainer.repaint();
        });
    }

    private static class WavePanel extends JPanel
    {
        private final JLabel waveLabel;
        private final JButton spawnButton;
        private JButton reinforcementButton;
        private final JPanel buttonsPanel;
        private final JPanel placeholderPanel;
        private String spawnUrl;
        private String reinforcementUrl;

        public WavePanel(int waveNumber)
        {
            setLayout(new BorderLayout());
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            // Wave label
            waveLabel = new JLabel("Wave " + waveNumber);
            waveLabel.setForeground(Color.WHITE);
            waveLabel.setFont(waveLabel.getFont().deriveFont(Font.BOLD));
            waveLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
            add(waveLabel, BorderLayout.NORTH);

            // Buttons panel - always 2 columns
            buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new GridLayout(1, 2, 5, 0));
            buttonsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            spawnButton = createLinkButton("Spawn");
            buttonsPanel.add(spawnButton);

            // Add empty placeholder panel for the second column
            placeholderPanel = new JPanel();
            placeholderPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            buttonsPanel.add(placeholderPanel);

            add(buttonsPanel, BorderLayout.CENTER);
        }

        private JButton createLinkButton(String text)
        {
            JButton button = new JButton(text);
            button.setFocusPainted(false);
            button.setBackground(ColorScheme.DARK_GRAY_COLOR);
            button.setForeground(Color.GRAY);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(3, 5, 3, 5)
            ));
            button.setEnabled(false);
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));

            button.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    if (button.isEnabled())
                    {
                        button.setBackground(ColorScheme.DARK_GRAY_COLOR.brighter());
                    }
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    button.setBackground(ColorScheme.DARK_GRAY_COLOR);
                }
            });

            return button;
        }

        public void setSpawnUrl(String url)
        {
            this.spawnUrl = url;
            spawnButton.setEnabled(true);
            spawnButton.setForeground(Color.WHITE);

            // Remove old listeners and add new one
            for (var listener : spawnButton.getActionListeners())
            {
                spawnButton.removeActionListener(listener);
            }

            spawnButton.addActionListener(e -> openUrl(url));
        }

        public void setReinforcementUrl(String url)
        {
            this.reinforcementUrl = url;

            // Create reinforcement button only when reinforcements spawn
            if (reinforcementButton == null)
            {
                // Remove placeholder and add reinforcement button
                buttonsPanel.remove(placeholderPanel);

                reinforcementButton = createLinkButton("Reinforcements");
                buttonsPanel.add(reinforcementButton);
                buttonsPanel.revalidate();
                buttonsPanel.repaint();
            }

            reinforcementButton.setEnabled(true);
            reinforcementButton.setForeground(Color.WHITE);

            // Remove old listeners and add new one
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
                // Log error or show message
            }
        }
    }
}