package com.example.kai.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import org.springframework.stereotype.Component;

import com.example.kai.config.KaiProperties.Target;

// Local-folder adapter. The rest of Kai sees this through KnowledgeSourceAdapter only.
// location = folder path, id = path relative to it.
@Component
public class LocalFileAdapter implements KnowledgeSourceAdapter {

	// Read and rewritten as plain text
	private static final Set<String> TEXT = Set.of("md", "txt", "properties", "yml", "yaml", "json", "xml");
	// Text extracted with Tika for scanning only; Kai never rewrites these
	private static final Set<String> OFFICE = Set.of("docx", "pptx", "pdf", "doc", "ppt");

	private static final long MAX_TEXT_BYTES = 50_000; // keep prompts small
	private static final long MAX_OFFICE_BYTES = 10_000_000; // images make office files big; the text is small
	private static final int MAX_CHARS = 50_000; // cap on extracted text

	private final Tika tika = new Tika();

	public LocalFileAdapter() {
		tika.setMaxStringLength(MAX_CHARS);
	}

	@Override
	public String type() {
		return "local";
	}

	@Override
	public void validate(Target source) throws IOException {
		Path root = root(source.location());
		if (!Files.isDirectory(root)) {
			throw new IOException("Folder not found: " + root);
		}
	}

	// Supported files only; skips hidden folders (.git, .kai-backup) and anything too big
	@Override
	public List<String> list(Target source) throws IOException {
		Path root = root(source.location());
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(p -> !isHidden(root.relativize(p)))
					.filter(LocalFileAdapter::supported)
					.sorted()
					.map(p -> root.relativize(p).toString())
					.toList();
		}
	}

	@Override
	public String read(Target source, String id) throws IOException {
		Path file = resolve(source.location(), id);
		if (canWrite(source, id)) {
			return Files.readString(file);
		}
		try (InputStream in = Files.newInputStream(file)) {
			return tika.parseToString(in);
		}
		catch (TikaException e) {
			throw new IOException("Cannot extract text from " + id + ": " + e.getMessage(), e);
		}
	}

	@Override
	public boolean canWrite(Target source, String id) {
		return TEXT.contains(extension(Path.of(id)));
	}

	@Override
	public void write(Target source, String id, String content) throws IOException {
		if (!canWrite(source, id)) {
			throw new IOException(id + " is read-only in Kai (update it by hand)");
		}
		Files.writeString(resolve(source.location(), id), content);
	}

	@Override
	public byte[] readBytes(Target source, String id) throws IOException {
		return Files.readAllBytes(resolve(source.location(), id));
	}

	@Override
	public void writeBytes(Target source, String id, byte[] content) throws IOException {
		if (!canWrite(source, id)) {
			throw new IOException(id + " is read-only in Kai (update it by hand)");
		}
		Files.write(resolve(source.location(), id), content);
	}

	private static boolean supported(Path p) {
		String ext = extension(p);
		long size = p.toFile().length();
		return (TEXT.contains(ext) && size <= MAX_TEXT_BYTES) || (OFFICE.contains(ext) && size <= MAX_OFFICE_BYTES);
	}

	private static Path root(String location) {
		return Path.of(location).toAbsolutePath().normalize();
	}

	// Refuse ids like "../../etc/passwd" that escape the chosen folder
	private static Path resolve(String location, String id) {
		Path root = root(location);
		Path file = root.resolve(id).normalize();
		if (!file.startsWith(root)) {
			throw new IllegalArgumentException("Outside " + root + ": " + id);
		}
		return file;
	}

	private static String extension(Path p) {
		String name = p.getFileName().toString();
		return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
	}

	// ".git", ".DS_Store", and Office lock files like "~$guide.docx" (present while a file is open in Word)
	private static boolean isHidden(Path relative) {
		for (Path part : relative) {
			if (part.toString().startsWith(".") || part.toString().startsWith("~$")) {
				return true;
			}
		}
		return false;
	}
}
