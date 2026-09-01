using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using UnityEngine;

namespace PokerGame.Client.Utils
{
    public static class EventBus
    {
        private static readonly ConcurrentDictionary<Type, List<Delegate>> Listeners = new();

        public static void Subscribe<T>(Action<T> handler)
        {
            if (handler == null) return;
            var type = typeof(T);
            Listeners.AddOrUpdate(type,
                _ => new List<Delegate> { handler },
                (_, list) =>
                {
                    lock (list) { list.Add(handler); }
                    return list;
                });
        }

        public static void Unsubscribe<T>(Action<T> handler)
        {
            if (handler == null) return;
            var type = typeof(T);
            if (Listeners.TryGetValue(type, out var list))
            {
                lock (list) { list.Remove(handler); }
            }
        }

        public static void Publish<T>(T eventMessage)
        {
            if (eventMessage == null) return;
            var type = typeof(T);
            if (Listeners.TryGetValue(type, out var list))
            {
                List<Delegate> copy;
                lock (list) { copy = new List<Delegate>(list); }

                foreach (var listener in copy)
                {
                    try
                    {
                        ((Action<T>)listener)?.Invoke(eventMessage);
                    }
                    catch (Exception ex)
                    {
                        Debug.LogError($"[EventBus Error] Subscriber handling failed: {ex.Message}");
                    }
                }
            }
        }

        public static void Clear() => Listeners.Clear();
    }
}