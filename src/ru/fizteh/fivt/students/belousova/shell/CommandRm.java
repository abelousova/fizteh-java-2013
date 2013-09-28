package ru.fizteh.fivt.students.belousova.shell;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class CommandRm implements Command {
    private static final String name = "rm";
    public String getName() {
        return name;
    }

    public void execute(String args) throws IOException {
        Scanner scanner = new Scanner(args);
        scanner.next();
        String dirName = scanner.nextLine().substring(1);
        File directory = new File(dirName);

        if (!directory.isAbsolute()) {
            directory = new File(MainShell.currentDirectory, dirName);
        }

        try {
            if (directory.exists()) {
                DeleteFunctions.deleteDirectory(directory);
            } else {
                throw new IOException("cannot remove " + dirName + ": No such file or directory");
            }
        } catch (IOException e) {
            throw new IOException(name + ": " + e.getMessage());
        }
    }
}