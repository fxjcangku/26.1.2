package net.minecraft.util.profiling.metrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class MetricsRegistry {
	public static final MetricsRegistry INSTANCE = new MetricsRegistry();
	private final WeakHashMap<ProfilerMeasured, Void> measuredInstances = new WeakHashMap();

	private MetricsRegistry() {
	}

	public void add(final ProfilerMeasured profilerMeasured) {
		this.measuredInstances.put(profilerMeasured, null);
	}

	public List<MetricSampler> getRegisteredSamplers() {
		Map<String, List<MetricSampler>> samplersByName = (Map<String, List<MetricSampler>>)this.measuredInstances
			.keySet()
			.stream()
			.flatMap(measuredInstance -> measuredInstance.profiledMetrics().stream())
			.collect(Collectors.groupingBy(MetricSampler::getName));
		return aggregateDuplicates(samplersByName);
	}

	private static List<MetricSampler> aggregateDuplicates(final Map<String, List<MetricSampler>> potentialDuplicates) {
		return (List<MetricSampler>)potentialDuplicates.entrySet()
			.stream()
			.map(
				entry -> {
					String samplerName = (String)entry.getKey();
					List<MetricSampler> duplicateSamplers = (List<MetricSampler>)entry.getValue();
					return (MetricSampler)(duplicateSamplers.size() > 1
						? new MetricsRegistry.AggregatedMetricSampler(samplerName, duplicateSamplers)
						: (MetricSampler)duplicateSamplers.get(0));
				}
			)
			.collect(Collectors.toList());
	}

	private static class AggregatedMetricSampler extends MetricSampler {
		private final List<MetricSampler> delegates;

		private AggregatedMetricSampler(final String name, final List<MetricSampler> delegates) {
			super(
				name, ((MetricSampler)delegates.get(0)).getCategory(), () -> averageValueFromDelegates(delegates), () -> beforeTick(delegates), thresholdTest(delegates)
			);
			this.delegates = delegates;
		}

		private static MetricSampler.ThresholdTest thresholdTest(final List<MetricSampler> delegates) {
			return value -> delegates.stream().anyMatch(delegate -> delegate.thresholdTest != null ? delegate.thresholdTest.test(value) : false);
		}

		private static void beforeTick(final List<MetricSampler> delegates) {
			for (MetricSampler delegate : delegates) {
				delegate.onStartTick();
			}
		}

		private static double averageValueFromDelegates(final List<MetricSampler> delegates) {
			double aggregatedValue = 0.0;

			for (MetricSampler delegate : delegates) {
				aggregatedValue += delegate.getSampler().getAsDouble();
			}

			return aggregatedValue / delegates.size();
		}

		@Override
		public boolean equals(@Nullable final Object o) {
			if (this == o) {
				return true;
			} else if (o == null || this.getClass() != o.getClass()) {
				return false;
			} else if (!super.equals(o)) {
				return false;
			} else {
				MetricsRegistry.AggregatedMetricSampler that = (MetricsRegistry.AggregatedMetricSampler)o;
				return this.delegates.equals(that.delegates);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(new Object[]{super.hashCode(), this.delegates});
		}
	}
}
