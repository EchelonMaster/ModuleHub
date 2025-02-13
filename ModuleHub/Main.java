import shared.AbstractModule;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Main extends AbstractModule {

    private static final String MANIFEST_URL = "https://pastebin.com/raw/cPymZmpb";

    private JFrame frame;
    private JList<String> moduleList;
    private DefaultListModel<String> moduleListModel;
    private JList<String> versionList;
    private DefaultListModel<String> versionListModel;
    private JTextArea descriptionArea;
    private JButton downloadButton;
    private JSONArray modulesArray;

    public Main() {
        createAndShowGUI();
        loadManifest();
    }

    @Override
    public void start() {
        frame.setVisible(true);
    }

    @Override
    public void bringToFront() {
        if (frame != null) {
            frame.toFront();
            frame.repaint();
        }
    }

    @Override
    public void hideModule() {
        if (frame != null) {
            frame.setVisible(false);
        }
    }

    @Override
    public void showModule() {
        if (frame != null) {
            frame.setVisible(true);
        }
    }

    @Override
    public boolean isVisible() {
        return frame != null && frame.isVisible();
    }

    @Override
    protected void onClose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private void createAndShowGUI() {
        frame = new JFrame("Module Manager - Echelon Module");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        // Center panel: Modules, Versions, and Description
        JPanel centerPanel = new JPanel(new BorderLayout());

        // Split pane for Modules and Versions lists.
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        moduleListModel = new DefaultListModel<>();
        moduleList = new JList<>(moduleListModel);
        JScrollPane moduleScrollPane = new JScrollPane(moduleList);
        moduleScrollPane.setBorder(BorderFactory.createTitledBorder("Modules"));
        splitPane.setLeftComponent(moduleScrollPane);
        splitPane.setDividerLocation(300);

        versionListModel = new DefaultListModel<>();
        versionList = new JList<>(versionListModel);
        JScrollPane versionScrollPane = new JScrollPane(versionList);
        versionScrollPane.setBorder(BorderFactory.createTitledBorder("Versions"));
        splitPane.setRightComponent(versionScrollPane);

        centerPanel.add(splitPane, BorderLayout.CENTER);

        // Description panel with fixed height.
        descriptionArea = new JTextArea();
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        descriptionScrollPane.setBorder(BorderFactory.createTitledBorder("Description"));
        descriptionScrollPane.setPreferredSize(new Dimension(600, 150));
        centerPanel.add(descriptionScrollPane, BorderLayout.SOUTH);

        frame.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel: Download button.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Download & Unzip");
        bottomPanel.add(downloadButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners for selections and download action.
        moduleList.addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                populateVersionList();
            }
        });
        versionList.addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                updateDescription();
            }
        });
        downloadButton.addActionListener(e -> downloadAndUnzipModule());
    }

    private void loadManifest() {
        try {
            String manifestLinksText = fetchContentFromURL(MANIFEST_URL).trim();
            // Using a lookahead regex to split on each new URL.
            String[] manifestLinks = manifestLinksText.split("(?=https://)");
            modulesArray = new JSONArray();

            // Process each URL from the manifest.
            for (String link : manifestLinks) {
                link = link.trim();
                if (!link.isEmpty()) {
                    try {
                        String manifestJson = fetchContentFromURL(link).trim();
                        if (manifestJson.startsWith("[")) {
                            JSONArray releases = new JSONArray(manifestJson);
                            for (int i = 0; i < releases.length(); i++) {
                                JSONObject release = releases.getJSONObject(i);
                                String tagName = release.optString("tag_name", "unknown");
                                // Extract module name from the URL.
                                String moduleName = extractRepositoryName(link);
                                String description = release.optString("body", "No description available.");
                                
                                // Check compatibility by searching for the marker.
                                String compatibilityMarker = "#AbstractModule-" + getVersion();
                                boolean isCompatible = description.contains(compatibilityMarker);
                                if (!isCompatible) {
                                    tagName = tagName + " (incompatible)";
                                }
                                
                                String zipUrl = release.optString("zipball_url", "");
                                String htmlUrl = release.optString("html_url", "No URL available.");

                                // Create the version object.
                                JSONObject versionObj = new JSONObject();
                                versionObj.put("version", tagName);
                                versionObj.put("downloadUrl", zipUrl);
                                versionObj.put("description", description + "\n\nGitHub URL: " + htmlUrl);

                                // Check if a module with the same name already exists.
                                JSONObject existingModule = null;
                                for (int j = 0; j < modulesArray.length(); j++) {
                                    JSONObject mod = modulesArray.getJSONObject(j);
                                    if (mod.optString("name").equals(moduleName)) {
                                        existingModule = mod;
                                        break;
                                    }
                                }
                                if (existingModule != null) {
                                    // Add the version to the existing module.
                                    existingModule.getJSONArray("versions").put(versionObj);
                                } else {
                                    // Create a new module object.
                                    JSONObject moduleObj = new JSONObject();
                                    moduleObj.put("name", moduleName);
                                    moduleObj.put("latestVersion", tagName);
                                    JSONArray versionsArray = new JSONArray();
                                    versionsArray.put(versionObj);
                                    moduleObj.put("versions", versionsArray);
                                    modulesArray.put(moduleObj);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing manifest URL " + link + ": " + e.getMessage());
                    }
                }
            }

            // Populate the module list (left panel) with unique module names.
            moduleListModel.clear();
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject moduleObj = modulesArray.getJSONObject(i);
                String moduleName = moduleObj.optString("name", "Unnamed Module");
                moduleListModel.addElement(moduleName);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error loading manifest: " + ex.getMessage());
        }
    }


    private String extractRepositoryName(String url) {
        try {
            URL parsedUrl = new URL(url);
            String[] pathSegments = parsedUrl.getPath().split("/");
            if (pathSegments.length >= 4) {
                String repoName = pathSegments[3];
                return formatRepositoryName(repoName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown Module";
    }

    private String formatRepositoryName(String repoName) {
        if (repoName.endsWith("-")) {
            repoName = repoName.substring(0, repoName.length() - 1);
        }
        int dashIndex = repoName.indexOf('-');
        if (dashIndex > 0) {
            String mainPart = repoName.substring(0, dashIndex);
            String secondary = repoName.substring(dashIndex + 1).trim();
            return mainPart + " (" + secondary + ")";
        } else {
            return repoName;
        }
    }

    private void populateVersionList() {
        versionListModel.clear();
        int moduleIndex = moduleList.getSelectedIndex();
        if (moduleIndex == -1) return;
        try {
            JSONObject moduleObj = modulesArray.getJSONObject(moduleIndex);
            JSONArray versions = moduleObj.getJSONArray("versions");
            int bestIndex = -1;
            String bestVersion = "";
            for (int i = 0; i < versions.length(); i++) {
                JSONObject versionObj = versions.getJSONObject(i);
                String versionStr = versionObj.optString("version", "Unknown");
                versionListModel.addElement(versionStr);
                
                // Determine compatibility: if version string contains " (incompatible)", it's not compatible.
                boolean isCompatible = !versionStr.contains(" (incompatible)");
                // Remove the incompatibility suffix for comparison.
                String cleanVersion = versionStr.replace(" (incompatible)", "").trim();
                if (isCompatible) {
                    if (bestIndex == -1) {
                        bestIndex = i;
                        bestVersion = cleanVersion;
                    } else {
                        // Compare versions semantically.
                        int cmp = compareVersions(cleanVersion, bestVersion);
                        if (cmp > 0) {
                            bestIndex = i;
                            bestVersion = cleanVersion;
                        }
                    }
                }
            }
            // If no compatible version was found, default to the first version.
            if (bestIndex == -1 && versionListModel.getSize() > 0) {
                bestIndex = 0;
            }
            if (bestIndex != -1) {
                versionList.setSelectedIndex(bestIndex);
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Compares two version strings (e.g., "v2.0.0" vs. "v1.8.3").
     * Returns a positive number if v1 > v2, negative if v1 < v2, or 0 if equal.
     */
    private int compareVersions(String v1, String v2) {
        // Remove leading 'v' or 'V' if present.
        if (v1.startsWith("v") || v1.startsWith("V")) {
            v1 = v1.substring(1);
        }
        if (v2.startsWith("v") || v2.startsWith("V")) {
            v2 = v2.substring(1);
        }
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    /**
     * Parses a part of a version string to an integer.
     * If the part cannot be parsed (e.g., it contains non-numeric characters), it returns 0.
     */
    private int parseVersionPart(String part) {
        try {
            // Remove any non-digit characters.
            part = part.replaceAll("[^0-9]", "");
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    private void updateDescription() {
        int moduleIndex = moduleList.getSelectedIndex();
        int versionIndex = versionList.getSelectedIndex();
        if (moduleIndex == -1 || versionIndex == -1) {
            descriptionArea.setText("");
            return;
        }
        try {
            JSONObject moduleObj = modulesArray.getJSONObject(moduleIndex);
            JSONArray versions = moduleObj.getJSONArray("versions");
            JSONObject versionObj = versions.getJSONObject(versionIndex);
            String description = versionObj.optString("description", "No description available.");
            descriptionArea.setText(description);
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    private String fetchContentFromURL(String urlStr) throws Exception {
        StringBuilder sb = new StringBuilder();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void downloadAndUnzipModule() {
        int moduleIndex = moduleList.getSelectedIndex();
        int versionIndex = versionList.getSelectedIndex();
        if (moduleIndex == -1 || versionIndex == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a module and version first.");
            return;
        }
        // Get the selected version string from the model.
        String selectedVersion = versionListModel.getElementAt(versionIndex);
        // If the version is marked as incompatible, ask for confirmation.
        if (selectedVersion.contains(" (incompatible)")) {
            int result = JOptionPane.showConfirmDialog(
                frame,
                "The selected version is marked as incompatible.\nDo you want to proceed?",
                "Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result != JOptionPane.YES_OPTION) {
                return; // Cancel the download.
            }
        }
        new Thread(() -> {
            try {
                JSONObject moduleObj = modulesArray.getJSONObject(moduleIndex);
                JSONArray versions = moduleObj.getJSONArray("versions");
                JSONObject versionObj = versions.getJSONObject(versionIndex);
                String downloadUrl = versionObj.optString("downloadUrl", "");
                if (downloadUrl.isEmpty()) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame, "No download URL available for this version.")
                    );
                    return;
                }
                String moduleName = moduleObj.optString("name", "Unnamed Module");
                String userHome = System.getProperty("user.home");
                String targetDirectoryPath = userHome + File.separator + "Documents" + File.separator
                        + "echelon" + File.separator + "desktop" + File.separator + "module"
                        + File.separator + moduleName;
                File targetDirectory = new File(targetDirectoryPath);
                if (!targetDirectory.exists()) {
                    targetDirectory.mkdirs();
                }
                File tempZipFile = File.createTempFile("module", ".zip");
                downloadFile(downloadUrl, tempZipFile);
                unzipFile(tempZipFile, targetDirectory, moduleName);
                tempZipFile.delete();
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "Module downloaded and extracted to:\n" + targetDirectoryPath)
                );
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "Error downloading and unzipping module: " + ex.getMessage())
                );
            }
        }).start();
    }


    private void downloadFile(String urlStr, File destination) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private void unzipFile(File zipFile, File destDir, String moduleName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String newEntryName = null;
                int index = entryName.indexOf(moduleName);
                if (index != -1) {
                    newEntryName = entryName.substring(index + moduleName.length());
                    while (newEntryName.startsWith("/") || newEntryName.startsWith("\\")) {
                        newEntryName = newEntryName.substring(1);
                    }
                }
                if (newEntryName == null || newEntryName.isEmpty()) {
                    newEntryName = new File(entryName).getName();
                }
                if (newEntryName.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }
                File outFile = new File(destDir, newEntryName);
                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static void main(String[] args) {
        Main module = new Main();
        module.start();
    }
}
