package me.athlaeos.valhallammo.utility;

import me.athlaeos.valhallammo.ValhallaMMO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Zipper {
    public static void zipFolder(String sourceDirPath, String zipFilePath) {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
            File source = new File(sourceDirPath);
            if (source.isDirectory()) {
                File[] children = source.listFiles();
                if (children == null) throw new IOException("Could not read " + sourceDirPath);
                // Place the directory's contents at the ZIP root, not the directory itself.
                for (File child : children) {
                    addToZip(child, child.getName(), output);
                }
            } else {
                addToZip(source, source.getName(), output);
            }
        } catch (IOException ex) {
            ValhallaMMO.logWarning("Could not zip resource pack");
            ex.printStackTrace();
        }
    }

    private static void addToZip(File source, String entryName, ZipOutputStream output) throws IOException {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Could not read " + source);
            for (File child : children) {
                addToZip(child, entryName + "/" + child.getName(), output);
            }
            return;
        }

        try (FileInputStream input = new FileInputStream(source)) {
            output.putNextEntry(new ZipEntry(entryName));
            input.transferTo(output);
            output.closeEntry();
        }
    }

    public static void unzipFolder(String zipFile, File destination){
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zipFile))){
            ZipEntry entry = in.getNextEntry();
            while (entry != null){
                File newFile = newFile(destination, entry);
                if (entry.isDirectory()){
                    if (!newFile.isDirectory() && !newFile.mkdirs()) throw new IOException("Could not make directory for zipped file " + entry.getName());
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not make directory for zipped file " + entry.getName());

                    try (FileOutputStream out = new FileOutputStream(newFile)) {
                        in.transferTo(out);
                    }
                }
                entry = in.getNextEntry();
            }
        } catch (IOException ex){
            ValhallaMMO.logWarning("Could not unzip " + zipFile);
            ex.printStackTrace();
        }
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}
