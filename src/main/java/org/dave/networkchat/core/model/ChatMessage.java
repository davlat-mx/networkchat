package org.dave.networkchat.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private String room;
    private String sender;
    private String text;
    private long timestamp;
    private ChatMessageType type;
}