package com.github.ylgrgyq.reservoir.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class FileName {
    private static final Logger logger = LoggerFactory.getLogger(FileName.class.getName());
    private static final String CURRENT_FILE_PREFIX = "CURRENT";
    private static final String LOCK_FILE_PREFIX = "LOCK";
    private static final String LOG_FILE_PREFIX = "LOG";
    private static final String MANIFEST_FILE_PREFIX = "MANIFEST";
    private static final String CONSUMER_COMMITTED_ID_FILE_PREFIX = "CONSUMER";

    static String getCurrentFileName() {
        return CURRENT_FILE_PREFIX;
    }

    private static String generateFileName(String prefix, int fileNumber, String suffix) {
        return String.format("%s-%07d.%s", prefix, fileNumber, suffix);
    }

    private static String generateFileName(int fileNumber, String suffix) {
        return String.format("%07d.%s", fileNumber, suffix);
    }

    static String getLockFileName() {
        return LOCK_FILE_PREFIX;
    }

    static String getSSTableName(int fileNumber) {
        return generateFileName(fileNumber, "sst");
    }

    static String getLogFileName(int fileNumber) {
        return generateFileName(LOG_FILE_PREFIX, fileNumber, "log");
    }

    static String getConsumerCommittedIdFileName(int fileNumber) {
        return generateFileName(CONSUMER_COMMITTED_ID_FILE_PREFIX, fileNumber, "commit");
    }

    static String getManifestFileName(int fileNumber) {
        return generateFileName(MANIFEST_FILE_PREFIX, fileNumber, "mf");
    }

    static void setCurrentFile(String baseDir, int manifestFileNumber) throws IOException {
        Path tmpPath = Files.createTempFile(Paths.get(baseDir), "CURRENT_TMP", ".tmp_mf");
        try {
            String manifestFileName = getManifestFileName(manifestFileNumber);

            Files.write(tmpPath, manifestFileName.getBytes(StandardCharsets.UTF_8), StandardOpenOption.SYNC,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);

            Files.move(tmpPath, Paths.get(baseDir, getCurrentFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.deleteIfExists(tmpPath);
            throw ex;
        }
    }

    static FileNameMeta parseFileName(String fileName) {
        if (fileName.startsWith("/") || fileName.startsWith("./")) {
            String[] strs = fileName.split("/");
            assert strs.length > 0;
            fileName = strs[strs.length - 1];
        }

        FileType type = FileType.Unknown;
        int fileNumber = 0;
        if (fileName.endsWith(CURRENT_FILE_PREFIX)) {
            type = FileType.Current;
        } else if (fileName.endsWith(".lock")) {
            type = FileType.Lock;
        } else if (fileName.endsWith(".tmp_mf")) {
            type = FileType.TempManifest;
        } else {
            String[] strs = fileName.split("[\\-.]", 3);
            if (strs.length == 3) {
                fileNumber = Integer.parseInt(strs[1]);
                switch (strs[2]) {
                    case "sst":
                        type = FileType.SSTable;
                        break;
                    case "log":
                        type = FileType.Log;
                        break;
                    case "mf":
                        type = FileType.Manifest;
                        break;
                    case "commit":
                        type = FileType.ConsumerCommit;
                        break;
                    default:
                        break;
                }
            }
        }
        return new FileNameMeta(fileName, fileNumber, type);
    }

    private static List<Path> getOutdatedFiles(String baseDir, int dataLogFileNumber, int consumerCommittedIdLogFileNumber, TableCache tableCache) {
        try {
            File dirFile = new File(baseDir);
            File[] files = dirFile.listFiles();

            if (files != null) {
                return Arrays.stream(files)
                        .filter(File::isFile)
                        .map(File::getName)
                        .map(FileName::parseFileName)
                        .filter(meta -> {
                            switch (meta.getType()) {
                                case Current:
                                case Lock:
                                case TempManifest:
                                case Manifest:
                                    return false;
                                case Unknown:
                                    return false;
                                case Log:
                                    return meta.getFileNumber() < dataLogFileNumber;
                                case SSTable:
                                    return !tableCache.hasTable(meta.getFileNumber());
                                default:
                                    return false;
                            }
                        })
                        .map(meta -> Paths.get(baseDir, meta.getFileName()))
                        .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        } catch (Throwable ex) {
            logger.error("get outdated files under dir:{} failed", baseDir, ex);
            return Collections.emptyList();
        }
    }

    static void deleteOutdatedFiles(String baseDir, int dataLogFileNumber, int consumerCommittedIdLogFileNumber, TableCache tableCache) {
        List<Path> outdatedFilePaths = getOutdatedFiles(baseDir, dataLogFileNumber, consumerCommittedIdLogFileNumber, tableCache);
        try {
            for (Path path : outdatedFilePaths) {
                Files.deleteIfExists(path);
            }
        } catch (Throwable t) {
            logger.error("delete outdated files:{} failed", outdatedFilePaths, t);
        }
    }

    static class FileNameMeta {
        private final String fileName;
        private final int fileNumber;
        private final FileType type;

        FileNameMeta(String fileName, int fileNumber, FileType type) {
            this.fileName = fileName;
            this.fileNumber = fileNumber;
            this.type = type;
        }

        String getFileName() {
            return fileName;
        }

        int getFileNumber() {
            return fileNumber;
        }

        FileType getType() {
            return type;
        }
    }

    enum FileType {
        Unknown,
        SSTable,
        Current,
        Log,
        Manifest,
        ConsumerCommit,
        TempManifest,
        Lock
    }
}
