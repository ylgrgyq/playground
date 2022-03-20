package consensus

import (
	"context"
	"time"
)

type TimeoutCallback func()

type Scheduler interface {
	ScheduleOnce(ctx context.Context, delay time.Duration, callback TimeoutCallback)
	SchedulePeriod(ctx context.Context, initialDelay time.Duration, interval time.Duration, callback TimeoutCallback)
}

type DefaultScheduler struct {
}

func (d *DefaultScheduler) Start() {

	ticker := time.NewTicker(100)
	ticker.Stop()
	go func() {

	}()
}

func (d *DefaultScheduler) ScheduleOnce(ctx context.Context, delay time.Duration, callback TimeoutCallback) {
	go func() {
		timer := time.NewTimer(delay)
		defer timer.Stop()

		select {
		case <-timer.C:
			callback()
		case <-ctx.Done():
			return
		}
	}()
}

func (d *DefaultScheduler) SchedulePeriod(ctx context.Context, initialDelay time.Duration, interval time.Duration, callback TimeoutCallback) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for {
			select {
			case <-ticker.C:
				callback()
			case <-ctx.Done():
				return
			}
		}
	}()
}

func NewScheduler() Scheduler{
	return &DefaultScheduler{}
}