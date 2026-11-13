package codeit.ootd.domain.directmessage.event;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;

public record DirectMessageSentEvent(DirectMessageDto message) {
}
