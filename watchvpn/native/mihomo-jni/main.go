package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"os"
	"path/filepath"
	"runtime"
	"sync"

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
)

func main() {}

//export MihomoStart
func MihomoStart(homeDir *C.char, configPath *C.char) *C.char {
	mu.Lock()
	defer mu.Unlock()
	defer func() {
		if rec := recover(); rec != nil {
			running = false
		}
	}()
	runtime.GOMAXPROCS(2)
	home := C.GoString(homeDir)
	cfgPath := C.GoString(configPath)
	if home == "" || cfgPath == "" {
		return C.CString("mihomo home/config path is empty")
	}
	if running {
		safeShutdown()
		running = false
	}
	_ = os.Setenv("SKIP_SAFE_PATH_CHECK", "true")
	if err := os.MkdirAll(home, 0o700); err != nil {
		return C.CString(err.Error())
	}
	constant.SetHomeDir(home)
	constant.SetConfig(cfgPath)
	openLog(filepath.Join(home, "mihomo.log"))
	buf, err := os.ReadFile(cfgPath)
	if err != nil {
		return C.CString(err.Error())
	}
	if err := hub.Parse(buf); err != nil {
		return C.CString(err.Error())
	}
	running = true
	return C.CString("")
}

//export MihomoStop
func MihomoStop() {
	mu.Lock()
	defer mu.Unlock()
	if running {
		safeShutdown()
		running = false
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
		return
	}
	logFile = f
	logrus.SetOutput(f)
	mihomolog.SetLevel(mihomolog.ERROR)
}
