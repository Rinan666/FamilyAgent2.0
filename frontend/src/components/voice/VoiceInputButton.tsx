'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Mic, MicOff } from 'lucide-react';

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: { error?: string }) => void) | null;
  start: () => void;
  stop: () => void;
  abort?: () => void;
};

type SpeechRecognitionEventLike = {
  resultIndex: number;
  results: ArrayLike<{
    isFinal: boolean;
    0: { transcript: string };
  }>;
};

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

type SpeechWindow = Window & {
  SpeechRecognition?: SpeechRecognitionConstructor;
  webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

interface VoiceInputButtonProps {
  onTranscript: (text: string) => void;
  disabled?: boolean;
  className?: string;
  compact?: boolean;
}

const RESTART_DELAY_MS = 250;

export default function VoiceInputButton({
  onTranscript,
  disabled = false,
  className = '',
  compact = false,
}: VoiceInputButtonProps) {
  const recognitionConstructorRef = useRef<SpeechRecognitionConstructor | null>(null);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const listeningRef = useRef(false);
  const manualStopRef = useRef(false);
  const restartTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onTranscriptRef = useRef(onTranscript);
  const disabledRef = useRef(disabled);
  const [supported, setSupported] = useState(true);
  const [listening, setListening] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    onTranscriptRef.current = onTranscript;
  }, [onTranscript]);

  useEffect(() => {
    disabledRef.current = disabled;
  }, [disabled]);

  const clearRestartTimer = useCallback(() => {
    if (restartTimerRef.current) {
      clearTimeout(restartTimerRef.current);
      restartTimerRef.current = null;
    }
  }, []);

  const detachRecognition = useCallback((recognition: SpeechRecognitionLike | null) => {
    if (!recognition) return;
    recognition.onresult = null;
    recognition.onerror = null;
    recognition.onend = null;
  }, []);

  const startFreshRecognition = useCallback(() => {
    const Recognition = recognitionConstructorRef.current;
    if (!Recognition || disabledRef.current) return;

    clearRestartTimer();
    detachRecognition(recognitionRef.current);
    recognitionRef.current = null;

    const recognition = new Recognition();
    recognition.lang = 'zh-CN';
    recognition.continuous = true;
    recognition.interimResults = false;

    recognition.onresult = (event) => {
      const parts: string[] = [];
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        if (result?.isFinal) {
          const transcript = result[0]?.transcript?.trim();
          if (transcript) parts.push(transcript);
        }
      }
      if (parts.length > 0) {
        onTranscriptRef.current(parts.join('\n'));
      }
    };

    recognition.onerror = (event) => {
      const errorType = event.error || '';
      if (errorType === 'not-allowed' || errorType === 'service-not-allowed') {
        manualStopRef.current = true;
        listeningRef.current = false;
        setListening(false);
        setError('麦克风未授权，请在浏览器地址栏允许麦克风权限');
        return;
      }

      if (errorType === 'audio-capture') {
        setError('没有检测到可用麦克风');
        return;
      }

      setError(errorType === 'no-speech' ? '没有听清，正在继续监听...' : '语音识别中断，正在尝试恢复...');
    };

    recognition.onend = () => {
      detachRecognition(recognition);
      if (recognitionRef.current === recognition) {
        recognitionRef.current = null;
      }

      if (manualStopRef.current || disabledRef.current) {
        listeningRef.current = false;
        setListening(false);
        return;
      }

      listeningRef.current = true;
      setListening(true);
      clearRestartTimer();
      restartTimerRef.current = setTimeout(() => {
        restartTimerRef.current = null;
        startFreshRecognition();
      }, RESTART_DELAY_MS);
    };

    recognitionRef.current = recognition;

    try {
      recognition.start();
      listeningRef.current = true;
      setListening(true);
    } catch {
      detachRecognition(recognition);
      recognitionRef.current = null;
      listeningRef.current = false;
      setListening(false);
      setError('语音识别启动失败，请稍后再试');
    }
  }, [clearRestartTimer, detachRecognition]);

  const stopListening = useCallback(() => {
    manualStopRef.current = true;
    clearRestartTimer();

    const recognition = recognitionRef.current;
    recognitionRef.current = null;
    listeningRef.current = false;
    setListening(false);

    if (!recognition) return;
    detachRecognition(recognition);
    try {
      recognition.abort?.();
      recognition.stop();
    } catch {
      // Browser speech recognition may throw if it already ended.
    }
  }, [clearRestartTimer, detachRecognition]);

  const startListening = useCallback(() => {
    if (!recognitionConstructorRef.current || disabledRef.current || listeningRef.current) return;
    setError('');
    manualStopRef.current = false;
    startFreshRecognition();
  }, [startFreshRecognition]);

  useEffect(() => {
    const speechWindow = window as SpeechWindow;
    const Recognition = speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
    recognitionConstructorRef.current = Recognition || null;
    setSupported(Boolean(Recognition));

    return () => {
      stopListening();
      recognitionConstructorRef.current = null;
    };
  }, [stopListening]);

  useEffect(() => {
    if (disabled && listeningRef.current) {
      stopListening();
    }
  }, [disabled, stopListening]);

  const toggleListening = () => {
    if (disabled) return;
    if (listening) {
      stopListening();
      return;
    }
    startListening();
  };

  if (!supported) {
    return (
      <span className={`inline-flex h-9 items-center rounded-lg border border-gray-200 px-3 text-xs text-gray-400 ${className}`}>
        当前浏览器不支持语音
      </span>
    );
  }

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className}`}>
      <button
        type="button"
        onClick={toggleListening}
        disabled={disabled}
        title={listening ? '停止录入' : '语音录入'}
        className={`inline-flex h-9 items-center justify-center gap-2 rounded-lg border text-xs font-medium transition-colors disabled:opacity-50 ${
          compact ? 'w-9 px-0' : 'px-3'
        } ${
          listening
            ? 'border-red-200 bg-red-50 text-red-700 hover:bg-red-100'
            : 'border-blue-100 bg-blue-50 text-blue-700 hover:bg-blue-100'
        }`}
      >
        {listening ? <MicOff className="h-3.5 w-3.5" /> : <Mic className="h-3.5 w-3.5" />}
        {!compact && (listening ? '停止录入' : '语音录入')}
      </button>
      {listening && !compact && <span className="text-xs text-blue-600">正在听...</span>}
      {error && <span className="text-xs text-red-500">{error}</span>}
    </div>
  );
}
