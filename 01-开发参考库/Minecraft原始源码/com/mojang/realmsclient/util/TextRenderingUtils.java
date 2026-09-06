package com.mojang.realmsclient.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TextRenderingUtils {
	private TextRenderingUtils() {
	}

	@VisibleForTesting
	protected static List<String> lineBreak(final String text) {
		return Arrays.asList(text.split("\\n"));
	}

	public static List<TextRenderingUtils.Line> decompose(final String text, final TextRenderingUtils.LineSegment... links) {
		return decompose(text, Arrays.asList(links));
	}

	private static List<TextRenderingUtils.Line> decompose(final String text, final List<TextRenderingUtils.LineSegment> links) {
		List<String> brokenLines = lineBreak(text);
		return insertLinks(brokenLines, links);
	}

	private static List<TextRenderingUtils.Line> insertLinks(final List<String> lines, final List<TextRenderingUtils.LineSegment> links) {
		int linkCount = 0;
		List<TextRenderingUtils.Line> processedLines = Lists.<TextRenderingUtils.Line>newArrayList();

		for (String line : lines) {
			List<TextRenderingUtils.LineSegment> segments = Lists.<TextRenderingUtils.LineSegment>newArrayList();

			for (String part : split(line, "%link")) {
				if ("%link".equals(part)) {
					segments.add((TextRenderingUtils.LineSegment)links.get(linkCount++));
				} else {
					segments.add(TextRenderingUtils.LineSegment.text(part));
				}
			}

			processedLines.add(new TextRenderingUtils.Line(segments));
		}

		return processedLines;
	}

	public static List<String> split(final String line, final String delimiter) {
		if (delimiter.isEmpty()) {
			throw new IllegalArgumentException("Delimiter cannot be the empty string");
		} else {
			List<String> parts = Lists.<String>newArrayList();
			int searchStart = 0;

			int matchIndex;
			while ((matchIndex = line.indexOf(delimiter, searchStart)) != -1) {
				if (matchIndex > searchStart) {
					parts.add(line.substring(searchStart, matchIndex));
				}

				parts.add(delimiter);
				searchStart = matchIndex + delimiter.length();
			}

			if (searchStart < line.length()) {
				parts.add(line.substring(searchStart));
			}

			return parts;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Line {
		public final List<TextRenderingUtils.LineSegment> segments;

		Line(final TextRenderingUtils.LineSegment... segments) {
			this(Arrays.asList(segments));
		}

		Line(final List<TextRenderingUtils.LineSegment> segments) {
			this.segments = segments;
		}

		public String toString() {
			return "Line{segments=" + this.segments + "}";
		}

		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			} else if (o != null && this.getClass() == o.getClass()) {
				TextRenderingUtils.Line line = (TextRenderingUtils.Line)o;
				return Objects.equals(this.segments, line.segments);
			} else {
				return false;
			}
		}

		public int hashCode() {
			return Objects.hash(new Object[]{this.segments});
		}
	}

	@Environment(EnvType.CLIENT)
	public static class LineSegment {
		private final String fullText;
		@Nullable
		private final String linkTitle;
		@Nullable
		private final String linkUrl;

		private LineSegment(final String fullText) {
			this.fullText = fullText;
			this.linkTitle = null;
			this.linkUrl = null;
		}

		private LineSegment(final String fullText, @Nullable final String linkTitle, @Nullable final String linkUrl) {
			this.fullText = fullText;
			this.linkTitle = linkTitle;
			this.linkUrl = linkUrl;
		}

		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			} else if (o != null && this.getClass() == o.getClass()) {
				TextRenderingUtils.LineSegment segment = (TextRenderingUtils.LineSegment)o;
				return Objects.equals(this.fullText, segment.fullText)
					&& Objects.equals(this.linkTitle, segment.linkTitle)
					&& Objects.equals(this.linkUrl, segment.linkUrl);
			} else {
				return false;
			}
		}

		public int hashCode() {
			return Objects.hash(new Object[]{this.fullText, this.linkTitle, this.linkUrl});
		}

		public String toString() {
			return "Segment{fullText='" + this.fullText + "', linkTitle='" + this.linkTitle + "', linkUrl='" + this.linkUrl + "'}";
		}

		public String renderedText() {
			return this.isLink() ? this.linkTitle : this.fullText;
		}

		public boolean isLink() {
			return this.linkTitle != null;
		}

		public String getLinkUrl() {
			if (!this.isLink()) {
				throw new IllegalStateException("Not a link: " + this);
			} else {
				return this.linkUrl;
			}
		}

		public static TextRenderingUtils.LineSegment link(final String linkTitle, final String linkUrl) {
			return new TextRenderingUtils.LineSegment(null, linkTitle, linkUrl);
		}

		@VisibleForTesting
		protected static TextRenderingUtils.LineSegment text(final String fullText) {
			return new TextRenderingUtils.LineSegment(fullText);
		}
	}
}
