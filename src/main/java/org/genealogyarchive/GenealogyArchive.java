package org.genealogyarchive;

import org.genealogyarchive.cli.Cli;

/**
 * Executable entry point: {@code java -jar genealogy-archive.jar <command> --config=<file>}.
 * Short-lived commands exit with their code; {@code serve} and {@code worker} keep running until the
 * service manager stops them.
 */
public final class GenealogyArchive {

    private GenealogyArchive() {
    }

    public static void main(String[] args) {
        Cli.Outcome outcome = new Cli(System.getenv(), System.out, System.err).run(args);
        if (outcome.runningProcess() == null) {
            System.exit(outcome.exitCode());
        }
    }
}
