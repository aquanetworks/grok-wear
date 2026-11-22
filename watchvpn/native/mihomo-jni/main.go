package main

/*
#include <android/log.h>
#include <stdlib.h>
#define MIHOMO_TAG "mihomo"
static void mihomo_android_log(int prio, const char* msg) {
	__android_log_print(prio, MIHOMO_TAG, "%s", msg);
}
*/
import "C"

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"unsafe"

	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	mihomolog "github.com/metacubex/mihomo/log"
	"github.com/sirupsen/logrus"
)

var (
	mu      sync.Mutex
	running bool
	logFile *os.File
	logOnce sync.Once
)

func main() {}

type androidWriter struct{}

func (androidWriter) Write(p []byte) (int, error) {
	androidLog(int(C.ANDROID_LOG_DEBUG), string(p))
	return len(p), nil
}

func androidLog(prio int, msg string) {
	if msg == "" {
		return
	}
	c := C.CString(msg)
	C.mihomo_android_log(C.int(prio), c)
	C.free(unsafe.Pointer(c))
}

func startLogPump() {
	logOnce.Do(func() {
		go func() {
			sub := mihomolog.Subscribe()
			defer mihomolog.UnSubscribe(sub)
			for ev := range sub {
				line := ev.LogLevel.String() + " " + ev.Payload
				prio := int(C.ANDROID_LOG_DEBUG)
				switch ev.LogLevel {
				case mihomolog.ERROR:
					prio = int(C.ANDROID_LOG_ERROR)
				case mihomolog.WARNING:
					prio = int(C.ANDROID_LOG_WARN)
				case mihomolog.INFO:
					prio = int(C.ANDROID_LOG_INFO)
				}
				androidLog(prio, line)
				if logFile != nil {
					_, _ = logFile.WriteString(line + "\n")
				}
			}
		}()
	})
}

//export MihomoStart
func MihomoStart(homeDir *C.char, configPath *C.char) (result *C.char) {
	defer func() {
		if rec := recover(); rec != nil {
			androidLog(int(C.ANDROID_LOG_ERROR), fmt.Sprint("panic ", rec))
			mu.Lock()
			running = false
			mu.Unlock()
			result = C.CString(fmt.Sprint(rec))
		}
	}()
	runtime.GOMAXPROCS(2)
	home := C.GoString(homeDir)
	cfgPath := C.GoString(configPath)
	if home == "" || cfgPath == "" {
		return C.CString("mihomo home/config path is empty")
	}
	mu.Lock()
	if running {
		safeShutdown()
		running = false
	}
	mu.Unlock()
	_ = os.Setenv("SKIP_SAFE_PATH_CHECK", "true")
	if err := os.MkdirAll(home, 0o700); err != nil {
		return C.CString(err.Error())
	}
	constant.SetHomeDir(home)
	constant.SetConfig(cfgPath)
	openLog(filepath.Join(home, "mihomo.log"))
	mihomolog.SetLevel(mihomolog.DEBUG)
	startLogPump()
	androidLog(int(C.ANDROID_LOG_INFO), "MihomoStart home="+home+" version="+constant.Version)
	buf, err := os.ReadFile(cfgPath)
	if err != nil {
		return C.CString(err.Error())
	}
	if err := hub.Parse(buf); err != nil {
		androidLog(int(C.ANDROID_LOG_ERROR), "hub.Parse "+err.Error())
		return C.CString(err.Error())
	}
	mu.Lock()
	running = true
	mu.Unlock()
	androidLog(int(C.ANDROID_LOG_INFO), "hub.Parse ok, listeners starting")
	return C.CString("")
}

//export MihomoStop
func MihomoStop() {
	mu.Lock()
	defer mu.Unlock()
	if running {
		safeShutdown()
		running = false
		androidLog(int(C.ANDROID_LOG_INFO), "MihomoStop")
	}
}

//export MihomoRunning
func MihomoRunning() C.int {
	mu.Lock()
	defer mu.Unlock()
	if running {
		return 1
	}
	return 0
}

//export MihomoVersion
func MihomoVersion() *C.char {
	return C.CString(constant.Version)
}

func safeShutdown() {
	defer func() { _ = recover() }()
	executor.Shutdown()
}

func openLog(path string) {
	if logFile != nil {
		_ = logFile.Close()
		logFile = nil
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
	if err != nil {
		androidLog(int(C.ANDROID_LOG_ERROR), "openLog "+err.Error())
		return
	}
	logFile = f
	logrus.SetOutput(io.MultiWriter(f, androidWriter{}))
	logrus.SetLevel(logrus.DebugLevel)
}
