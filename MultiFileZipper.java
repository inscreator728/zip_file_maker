import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MultiFileZipper extends JFrame {
    private JButton selectAndZipButton;
    private JProgressBar progressBar;

    public MultiFileZipper() {
        super("Multi-File & Folder Zipper");
        initUI();
    }

    private void initUI() {
        selectAndZipButton = new JButton("Select & Zip...");
        selectAndZipButton.addActionListener(this::onSelectAndZip);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel panel = new JPanel();
        panel.add(selectAndZipButton);

        add(panel, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);

        setSize(400, 130);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void onSelectAndZip(ActionEvent e) {
        // 1) Choose sources (files/directories)
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File[] sources = chooser.getSelectedFiles();

        // 2) Choose destination ZIP
        JFileChooser saveChooser = new JFileChooser();
        saveChooser.setDialogTitle("Save ZIP As...");
        saveChooser.setSelectedFile(new File("archive.zip"));
        if (saveChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File destZip = saveChooser.getSelectedFile();
        if (!destZip.getName().toLowerCase().endsWith(".zip")) {
            destZip = new File(destZip.getAbsolutePath() + ".zip");
        }

        // 3) Confirm
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Compress " + sources.length + " item(s) into:\n" + destZip.getAbsolutePath() + "\nProceed?",
                "Confirm Compression",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION)
            return;

        // 4) Launch background zipping
        selectAndZipButton.setEnabled(false);
        ZipTask task = new ZipTask(sources, destZip);
        task.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        task.execute();
    }

    /**
     * SwingWorker to perform zip in background with progress updates.
     */
    private class ZipTask extends SwingWorker<Void, Integer> {
        private final File[] sources;
        private final File destZip;
        private final List<File> allFiles = new ArrayList<>();

        public ZipTask(File[] sources, File destZip) {
            this.sources = sources;
            this.destZip = destZip;
        }

        @Override
        protected Void doInBackground() throws Exception {
            // Gather all files (flatten directories)
            for (File src : sources) {
                gatherFiles(src);
            }
            int total = allFiles.size();
            int count = 0;

            // Ensure parent directories exist
            File parent = destZip.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Zip each file, updating progress
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZip))) {
                byte[] buffer = new byte[4096];
                for (File file : allFiles) {
                    String entryName = computeEntryName(file);
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (FileInputStream fis = new FileInputStream(file)) {
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();

                    // update progress
                    count++;
                    int prog = (int) ((count / (double) total) * 100);
                    setProgress(prog);
                }
            }

            return null;
        }

        /** Recursively collects files under 'f' (dirs) or adds file itself. */
        private void gatherFiles(File f) {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File child : children) {
                        gatherFiles(child);
                    }
                }
            } else {
                allFiles.add(f);
            }
        }

        /** Computes the path inside the ZIP, relative to the chosen root(s). */
        private String computeEntryName(File file) throws IOException {
            // Find which source root this file belongs to
            for (File root : sources) {
                String rootPath = root.getCanonicalPath();
                String filePath = file.getCanonicalPath();
                if (filePath.startsWith(rootPath)) {
                    String rel = filePath.substring(rootPath.length());
                    // ensure no leading file separator
                    if (rel.startsWith(File.separator))
                        rel = rel.substring(1);
                    // prepend root name so entries don't collide
                    return root.getName() + "/" + rel.replace(File.separatorChar, '/');
                }
            }
            // fallback
            return file.getName();
        }

        @Override
        protected void done() {
            try {
                get(); // throws exception if doInBackground failed
                JOptionPane.showMessageDialog(
                        MultiFileZipper.this,
                        "ZIP created successfully:\n" + destZip.getAbsolutePath(),
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (InterruptedException | ExecutionException ex) {
                JOptionPane.showMessageDialog(
                        MultiFileZipper.this,
                        "Error during compression:\n" + ex.getCause().getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                // Reset UI
                progressBar.setValue(0);
                selectAndZipButton.setEnabled(true);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MultiFileZipper zipper = new MultiFileZipper();
            zipper.setVisible(true);
        });
    }
}
