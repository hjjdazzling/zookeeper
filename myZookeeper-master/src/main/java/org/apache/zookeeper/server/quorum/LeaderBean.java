/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server.quorum;

import org.apache.zookeeper.server.ZooKeeperServerBean;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.quorum.LearnerHandler;
import org.apache.zookeeper.server.quorum.Leader;

/**
 * Leader MBean interface implementation.
 */
public class LeaderBean extends ZooKeeperServerBean implements LeaderMXBean {
    private final Leader leader;
    
    public LeaderBean(Leader leader, ZooKeeperServer zks) {
        super(zks);
        String string="";
        if(leader!=null){
            ProposalStats proposalStats = leader.getProposalStats();
            string = proposalStats.toString();
        }
        System.out.println("构建领导者----->"+string);
        this.leader = leader;
    }
    @Override
    public String getName() {
        return "Leader";
    }
    @Override
    public String getCurrentZxid() {
        return "0x" + Long.toHexString(zks.getZxid());
    }
    @Override
    public String followerInfo() {
        StringBuilder sb = new StringBuilder();
        for (LearnerHandler handler : leader.getLearners()) {
            sb.append(handler.toString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public long getElectionTimeTaken() {
        return leader.self.getElectionTimeTaken();
    }

    @Override
    public int getLastProposalSize() {
        return leader.getProposalStats().getLastProposalSize();
    }

    @Override
    public int getMinProposalSize() {
        return leader.getProposalStats().getMinProposalSize();
    }

    @Override
    public int getMaxProposalSize() {
        return leader.getProposalStats().getMaxProposalSize();
    }

    @Override
    public void resetProposalStatistics() {
        leader.getProposalStats().reset();
    }
}
