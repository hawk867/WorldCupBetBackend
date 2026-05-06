package org.danielesteban.worldcupbetbackend.service;

import org.danielesteban.worldcupbetbackend.domain.entity.UserScore;
import org.danielesteban.worldcupbetbackend.persistence.repository.UserScoreRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
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
        messagingTemplate.convertAndSend("/topic/ranking", getRanking());
    }
}
