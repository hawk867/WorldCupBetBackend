package org.danielesteban.worldcupbetbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.danielesteban.worldcupbetbackend.domain.entity.Match;
import org.danielesteban.worldcupbetbackend.domain.enums.MatchStatus;
import org.danielesteban.worldcupbetbackend.persistence.repository.MatchRepository;
import org.danielesteban.worldcupbetbackend.service.event.MatchAdjustedEvent;
import org.danielesteban.worldcupbetbackend.service.event.MatchFinishedEvent;
import org.danielesteban.worldcupbetbackend.service.exception.IllegalStateTransitionException;
import org.danielesteban.worldcupbetbackend.service.exception.ResourceNotFoundException;
import org.danielesteban.worldcupbetbackend.websocket.dto.MatchUpdateMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchService(MatchRepository matchRepository,
                        ApplicationEventPublisher eventPublisher,
                        SimpMessagingTemplate messagingTemplate) {
        this.matchRepository = matchRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<Match> findAll() {
        return matchRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Match> findByStage(Long stageId) {
        return matchRepository.findAllByStageIdOrderByKickoffAtAsc(stageId);
    }

    @Transactional(readOnly = true)
    public List<Match> findByStatus(MatchStatus status) {
        return matchRepository.findAllByStatus(status);
    }

    @Transactional(readOnly = true)
    public Match findById(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
    }

    @Transactional
    public Match transitionStatus(Long matchId, MatchStatus newStatus) {
        Match match = findById(matchId);

        if (!MatchStateMachine.isValidTransition(match.getStatus(), newStatus)) {
            throw new IllegalStateTransitionException(match.getStatus().name(), newStatus.name());
        }

        match.setStatus(newStatus);

        if (newStatus == MatchStatus.FINISHED) {
            eventPublisher.publishEvent(new MatchFinishedEvent(matchId));
        } else if (newStatus == MatchStatus.ADJUSTED) {
            eventPublisher.publishEvent(new MatchAdjustedEvent(matchId));
        }

        publishMatchUpdate(match);
        return match;
    }

    @Transactional
    public Match updateScore(Long matchId, Integer homeGoals, Integer awayGoals) {
        Match match = findById(matchId);
        match.setHomeGoals(homeGoals);
        match.setAwayGoals(awayGoals);
        publishMatchUpdate(match);
        return match;
    }

    private void publishMatchUpdate(Match match) {
        try {
            MatchUpdateMessage message = MatchUpdateMessage.from(match);
            messagingTemplate.convertAndSend("/topic/matches/" + match.getId(), message);
        } catch (Exception e) {
            log.warn("Error publicando actualización de partido {}: {}", match.getId(), e.getMessage());
        }
    }
}
