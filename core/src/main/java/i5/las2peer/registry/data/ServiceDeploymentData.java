package i5.las2peer.registry.data;

import i5.las2peer.registry.Util;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;

public class ServiceDeploymentData {
	private String serviceName;
	private String serviceClass;
	private BigInteger versionMajor;
	private BigInteger versionMinor;
	private BigInteger versionPatch;
	private String nodeId;
	private BigInteger timestamp;
	private boolean ended;
	private byte[] supplementHash;

	public ServiceDeploymentData(String serviceName, String serviceClass, BigInteger versionMajor,
			BigInteger versionMinor, BigInteger versionPatch, String nodeId, BigInteger timestamp) {
		this(serviceName, serviceClass, versionMajor, versionMinor, versionPatch, nodeId, timestamp, false);
	}

	public ServiceDeploymentData(String serviceName, String serviceClass, BigInteger versionMajor,
			BigInteger versionMinor, BigInteger versionPatch, String nodeId, BigInteger timestamp, boolean ended) {
		this.serviceName = serviceName;
		this.serviceClass = serviceClass;
		this.versionMajor = versionMajor;
		this.versionMinor = versionMinor;
		this.versionPatch = versionPatch;
		this.nodeId = nodeId;
		this.timestamp = timestamp;
		this.ended = ended;
	}

	public ServiceDeploymentData(String serviceName, BigInteger versionMajor, BigInteger versionMinor,
			BigInteger versionPatch, BigInteger timestamp, byte[] supplementHash) {
		this.serviceName = serviceName;
		this.versionMajor = versionMajor;
		this.versionMinor = versionMinor;
		this.versionPatch = versionPatch;
		this.timestamp = timestamp;
		this.supplementHash = supplementHash;
	}

	public ServiceDeploymentData(String serviceName, BigInteger versionMajor, BigInteger versionMinor,
			BigInteger versionPatch, BigInteger timestamp, boolean ended) {
		this.serviceName = serviceName;
		this.versionMajor = versionMajor;
		this.versionMinor = versionMinor;
		this.versionPatch = versionPatch;
		this.timestamp = timestamp;
		this.ended = ended;
	}

	public String getServicePackageName() {
		return serviceName;
	}

	public String getServiceClassName() {
		return serviceClass;
	}

	public String getVersion() {
		return versionMajor + "." + versionMinor + "." + versionPatch;
	}

	public String getNodeId() {
		return nodeId;
	}

	public byte[] getSupplementHash() {
		return supplementHash;
	}

	public String getTime() {
		if (timestamp == null) {
			return null;
		}
		return Util.unixtimeToString(timestamp.longValue());
	}

	public Instant getTimestamp() {
		return Instant.ofEpochSecond(timestamp.longValue());
	}

	public boolean hasEnded() {
		return ended;
	}

	@Override
	public String toString() {
		return "ServiceDeploymentData(package: " + this.getServicePackageName() + ", class: "
				+ this.getServiceClassName() + ", version: " + this.getVersion() + ", time: " + getTime()
				+ ", node ID: " + getNodeId() + ")";
	}

	/** Considered equal if all fields but timestamp match. */
	// the timestamp can be considered optional metadata
	// the intent here is to replace "outdated" instances when one with
	// a newer timestamp comes in
	@Override
	public boolean equals(Object o) {
		System.out.println("Doing the equal custom");
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ServiceDeploymentData that = (ServiceDeploymentData) o;
		return Objects.equals(serviceName, that.serviceName) && Objects.equals(serviceClass, that.serviceClass)
				&& Objects.equals(versionMajor, that.versionMajor) && Objects.equals(versionMinor, that.versionMinor)
				&& Objects.equals(versionPatch, that.versionPatch) && Objects.equals(nodeId, that.nodeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(serviceName, serviceClass, versionMajor, versionMinor, versionPatch, nodeId);
	}
}
