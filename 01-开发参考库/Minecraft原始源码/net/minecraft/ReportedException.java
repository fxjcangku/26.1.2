package net.minecraft;

public class ReportedException extends RuntimeException {
	private final CrashReport report;

	public ReportedException(final CrashReport report) {
		this.report = report;
	}

	public CrashReport getReport() {
		return this.report;
	}

	public Throwable getCause() {
		return this.report.getException();
	}

	public String getMessage() {
		return this.report.getTitle();
	}
}
