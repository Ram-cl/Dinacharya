package com.kanban.repository;

import com.kanban.model.entity.Team;
import com.kanban.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Page<Team> findByLead(User lead, Pageable pageable);

    @Query("""
        SELECT t FROM Team t 
        WHERE :member MEMBER OF t.members
        ORDER BY t.name ASC
        """)
    Page<Team> findTeamsByMember(@Param("member") User member, Pageable pageable);

    @Query("""
        SELECT t FROM Team t 
        WHERE t.lead.id = :leadId OR :member MEMBER OF t.members
        ORDER BY t.name ASC
        """)
    Page<Team> findTeamsByLeadOrMember(@Param("leadId") UUID leadId, @Param("member") User member, Pageable pageable);

    @Query("""
        SELECT t FROM Team t 
        WHERE :userId MEMBER OF t.members
        ORDER BY t.name ASC
        """)
    Set<Team> findAllTeamsByMemberId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(t) FROM Team t WHERE :member MEMBER OF t.members")
    long countTeamsByMember(@Param("member") User member);

    List<Team> findByLead_Id(UUID leadId);

    List<Team> findByNameIgnoreCase(String name);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM team_members WHERE user_id = :userId", nativeQuery = true)
    void removeFromAllTeams(@Param("userId") UUID userId);
}
