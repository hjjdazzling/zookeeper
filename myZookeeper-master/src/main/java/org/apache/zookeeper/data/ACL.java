

package org.apache.zookeeper.data;

import org.apache.jute.*;
import org.apache.yetus.audience.InterfaceAudience;
@InterfaceAudience.Public
public class ACL implements Record {
  private int perms;
  private Id id;
  public ACL() {
  }
  public ACL(
        int perms,
        Id id) {
    this.perms=perms;
    this.id=id;
  }
  public int getPerms() {
    return perms;
  }
  public void setPerms(int m_) {
    perms=m_;
  }
  public Id getId() {
    return id;
  }
  public void setId(Id m_) {
    id=m_;
  }
  @Override
  public void serialize(OutputArchive a_, String tag) throws java.io.IOException {
    a_.startRecord(this,tag);
    a_.writeInt(perms,"perms");
    a_.writeRecord(id,"id");
    a_.endRecord(this,tag);
  }
  @Override
  public void deserialize(InputArchive a_, String tag) throws java.io.IOException {
    a_.startRecord(tag);
    perms=a_.readInt("perms");
    id= new Id();
    a_.readRecord(id,"id");
    a_.endRecord(tag);
  }
  @Override
  public String toString() {
    try {
      java.io.ByteArrayOutputStream s =
        new java.io.ByteArrayOutputStream();
      CsvOutputArchive a_ = 
        new CsvOutputArchive(s);
      a_.startRecord(this,"");
    a_.writeInt(perms,"perms");
    a_.writeRecord(id,"id");
      a_.endRecord(this,"");
      return new String(s.toByteArray(), "UTF-8");
    } catch (Throwable ex) {
      ex.printStackTrace();
    }
    return "ERROR";
  }
  public void write(java.io.DataOutput out) throws java.io.IOException {
    BinaryOutputArchive archive = new BinaryOutputArchive(out);
    serialize(archive, "");
  }
  public void readFields(java.io.DataInput in) throws java.io.IOException {
    BinaryInputArchive archive = new BinaryInputArchive(in);
    deserialize(archive, "");
  }
  public int compareTo (Object peer_) throws ClassCastException {
    if (!(peer_ instanceof ACL)) {
      throw new ClassCastException("Comparing different types of records.");
    }
    ACL peer = (ACL) peer_;
    int ret = 0;
    ret = (perms == peer.perms)? 0 :((perms<peer.perms)?-1:1);
    if (ret != 0) return ret;
    ret = id.compareTo(peer.id);
    if (ret != 0) return ret;
     return ret;
  }
  @Override
  public boolean equals(Object peer_) {
    if (!(peer_ instanceof ACL)) {
      return false;
    }
    if (peer_ == this) {
      return true;
    }
    ACL peer = (ACL) peer_;
    boolean ret = false;
    ret = (perms==peer.perms);
    if (!ret) return ret;
    ret = id.equals(peer.id);
    if (!ret) return ret;
     return ret;
  }
  @Override
  public int hashCode() {
    int result = 17;
    int ret;
    ret = (int)perms;
    result = 37*result + ret;
    ret = id.hashCode();
    result = 37*result + ret;
    return result;
  }
  public static String signature() {
    return "LACL(iLId(ss))";
  }
}
