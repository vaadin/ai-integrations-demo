package com.vaadin.demo.nordicsupply.feedback;

/**
 * The feedback was not posted to Slack. It has no message and no cause, because the message of an I/O error contains
 * the webhook URL.
 */
public class FeedbackNotSentException extends RuntimeException {}
