package org.danielesteban.worldcupbetbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserScoreRepository;
import org.danielesteban.worldcupbetbackend.websocket.dto.RankingUpdateMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class RankingService {

    private final UserScoreRepository userScoreRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RankingService(UserScoreRepository userScoreRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.userScoreRepository = userScoreRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<UserScore> getRanking() {
        return userScoreRepository.findAllByOrderByTotalPointsDescExactCountDesc();
    }

    public void publishRanking() {
        try {
            List<UserScore> ranking = getRanking();
            RankingUpdateMessage message = RankingUpdateMessage.from(ranking);
            messagingTemplate.convertAndSend("/topic/ranking", message);
        } catch (Exception e) {
            log.warn("Error publicando ranking: {}", e.getMessage());
        }
    }
}
