package com.github.ruediste1.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.github.ruediste1.btrbck.DisplayException;
import com.github.ruediste1.btrbck.GuiceModule;
import com.github.ruediste1.btrbck.SnapshotTransferService;
import com.github.ruediste1.btrbck.StreamRepositoryService;
import com.github.ruediste1.btrbck.StreamService;
import com.github.ruediste1.btrbck.Util;
import com.github.ruediste1.btrbck.dom.ApplicationStreamRepository;
import com.github.ruediste1.btrbck.dom.BackupStreamRepository;
import com.github.ruediste1.btrbck.dom.RemoteRepository;
import com.github.ruediste1.btrbck.dom.Snapshot;
import com.github.ruediste1.btrbck.dom.SshTarget;
import com.github.ruediste1.btrbck.dom.Stream;
import com.github.ruediste1.btrbck.dom.StreamRepository;
import com.google.common.io.ByteStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class CliMain {
	@Option(name = "-r", usage = "the location of the stream repository to use")
	File repositoryLocation;

	@Option(name = "-c", usage = "if given, missing target streams will be created during the push, pull and the sync command")
	boolean createTargetStreams;

	@Option(name = "-a", usage = "if given, the initialize command creates an application stream repository")
	boolean applicationRepository;

	@Option(name = "-i", usage = "the key file to be used by ssh")
	File keyFile;

	@Argument(hidden = true)
	List<String> arguments = new ArrayList<>();

	@Inject
	StreamRepositoryService streamRepositoryService;

	@Inject
	StreamService streamService;

	@Inject
	SnapshotTransferService streamTransferService;

	public static void main(String... args) throws Exception {
		new CliMain().doMain(args);
	}

	private void doMain(String[] args) throws Exception {
		Injector injector = Guice.createInjector(new GuiceModule());
		Util.setInjector(injector);
		Util.injectMembers(this);

		try {
			processCommand(args);
		} catch (DisplayException e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		}
	}

	void processCommand(String[] args) throws IOException {
		parseCmdLine(args);

		String command = arguments.get(0);
		if ("snapshot".equals(command)) {
			cmdSnapshot();
		} else if ("list".equals(command)) {
			cmdList();
		} else if ("push".equals(command)) {
			cmdPush();
		} else if ("pull".equals(command)) {
			cmdPull();
		} else if ("process".equals(command)) {
			cmdProcess();
		} else if ("prune".equals(command)) {
			cmdPrune();
		} else if ("create".equals(command)) {
			cmdCreate();
		} else if ("delete".equals(command)) {
			cmdDelete();
		} else if ("restore".equals(command)) {
			cmdRestore();
		}
	}

	private void parseCmdLine(String[] args) {
		CmdLineParser parser = new CmdLineParser(this);

		// if you have a wider console, you could increase the value;
		// here 80 is also the default
		parser.setUsageWidth(80);

		try {
			// parse the arguments.
			parser.parseArgument(args);

			if (arguments.isEmpty()) {
				throw new CmdLineException(parser, "No command given");
			}
		} catch (CmdLineException e) {
			// if there's a problem in the command line,
			// you'll get this exception. this will report
			// an error message.
			System.err.println("Error: " + e.getMessage());

			try {
				ByteStreams.copy(getClass().getResourceAsStream("usage.txt"),
						System.err);
			} catch (IOException e1) {
				throw new RuntimeException("Error while printing usage", e1);
			}

			System.err.println("\n\nOptions: ");
			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			System.exit(1);
		}

	}

	private void cmdPrune() {
		// TODO Auto-generated method stub

	}

	private void cmdProcess() {
		// TODO Auto-generated method stub

	}

	private void cmdPush() {
		if (arguments.size() < 4) {
			throw new DisplayException("Not enought arguments");
		}

		if (arguments.size() > 5) {
			throw new DisplayException("Too many arguments");
		}

		String streamName = arguments.get(1);
		SshTarget sshTarget = SshTarget.parse(arguments.get(2)).withKeyFile(
				keyFile);

		String remoteStreamName = streamName;
		if (arguments.size() == 5) {
			remoteStreamName = arguments.get(4);
		}

		RemoteRepository remoteRepo = new RemoteRepository();
		remoteRepo.location = arguments.get(3);
		remoteRepo.sshTarget = sshTarget;

		StreamRepository repo = readRepository();
		Stream stream = streamService.readStream(repo, streamName);

		streamTransferService.push(stream, remoteRepo, remoteStreamName,
				createTargetStreams);
	}

	private void cmdPull() {
		if (arguments.size() < 4) {
			throw new DisplayException("Not enought arguments");
		}

		if (arguments.size() > 5) {
			throw new DisplayException("Too many arguments");
		}

		SshTarget sshTarget = SshTarget.parse(arguments.get(1)).withKeyFile(
				keyFile);
		String remoteRepoPath = arguments.get(2);

		String remoteStreamName = arguments.get(3);

		String streamName = remoteStreamName;
		if (arguments.size() == 5) {
			streamName = arguments.get(4);
		}

		RemoteRepository remoteRepo = new RemoteRepository();
		remoteRepo.location = remoteRepoPath;
		remoteRepo.sshTarget = sshTarget;

		StreamRepository repo = readRepository();

		streamTransferService.pull(repo, streamName, remoteRepo,
				remoteStreamName, createTargetStreams);

	}

	private void cmdList() {
		if (arguments.size() == 1) {
			// list streams in repository
			StreamRepository repo = readRepository();
			System.out.println("Streams in repository "
					+ repo.rootDirectory.toAbsolutePath() + ":");
			for (String name : streamService.getStreamNames(repo)) {
				System.out.println(name);
			}
		} else if (arguments.size() == 2) {
			StreamRepository repo = readRepository();
			Stream stream = streamService.readStream(repo, arguments.get(1));
			TreeMap<Integer, Snapshot> snapshots = streamService
					.getSnapshots(stream);
			System.out.println("Snapshots in stream " + stream.name
					+ " in repository " + repo.rootDirectory.toAbsolutePath()
					+ ":");
			for (Snapshot s : snapshots.values()) {
				System.out.println(s.getSnapshotName());
			}
		} else {
			throw new DisplayException("too many arguments");
		}

	}

	private void cmdCreate() throws IOException {
		if (arguments.size() == 1) {
			// create repository
			Path location;
			if (repositoryLocation != null) {
				location = repositoryLocation.toPath();
			} else {
				location = Paths.get("");
			}

			StreamRepository repo;
			if (applicationRepository) {
				repo = streamRepositoryService.createRepository(
						ApplicationStreamRepository.class, location);
			} else {
				repo = streamRepositoryService.createRepository(
						BackupStreamRepository.class, location);
			}

			System.out.println("Created repository in "
					+ repo.rootDirectory.toAbsolutePath());
		} else if (arguments.size() == 2) {
			// create stream
			String streamName = arguments.get(1);
			StreamRepository repo = readRepository();
			streamService.createStream(repo, streamName);
		} else {
			throw new DisplayException("too many arguments");
		}
	}

	private void cmdDelete() {
		if (arguments.size() == 1) {
			StreamRepository repo = readRepository();
			// delete repository
			streamService.deleteStreams(repo);
			streamRepositoryService.deleteEmptyRepository(repo);
		} else if (arguments.size() == 2) {
			StreamRepository repo = readRepository();
			streamService.deleteStream(repo, arguments.get(1));
		} else {
			throw new DisplayException("too many arguments");
		}

	}

	private void cmdSnapshot() {
		if (arguments.size() == 1) {
			StreamRepository repo = readRepository();
			for (String name : streamService.getStreamNames(repo)) {
				Stream stream = streamService.readStream(repo, name);
				streamService.takeSnapshot(stream);
			}
		} else if (arguments.size() == 2) {
			StreamRepository repo = readRepository();
			Stream stream = streamService.readStream(repo, arguments.get(1));
			streamService.takeSnapshot(stream);
		} else {
			throw new DisplayException("too many arguments");
		}
	}

	private void cmdRestore() {
		if (arguments.size() == 1) {
			// restore the latest snapshot of all streams
			StreamRepository repo = readRepository();
			for (String name : streamService.getStreamNames(repo)) {
				Stream stream = streamService.readStream(repo, name);
				streamService.restoreLatestSnapshot(stream);
			}
		} else if (arguments.size() == 2) {
			// restore the latest snapshot of a single stream
			StreamRepository repo = readRepository();
			Stream stream = streamService.readStream(repo, arguments.get(1));
			streamService.restoreLatestSnapshot(stream);
		} else if (arguments.size() == 3) {
			// restore a specific snapshot of a single stream
			StreamRepository repo = readRepository();
			Stream stream = streamService.readStream(repo, arguments.get(1));
			int snapshotNr = Integer.parseInt(arguments.get(2));
			streamService.restoreSnapshot(stream, snapshotNr);
		} else {
			throw new DisplayException("too many arguments");
		}

	}

	private StreamRepository readRepository() {
		File path = repositoryLocation;
		if (path == null) {
			path = Paths.get("").toFile();
		}
		return streamRepositoryService.readRepository(path.toPath());
	}
}
