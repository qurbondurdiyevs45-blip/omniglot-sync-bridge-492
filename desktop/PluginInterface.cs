using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Collections.Generic;

namespace OmniGlot.SyncBridge
{
    public enum SyncScope
    {
        EnvironmentVariables = 0,
        UIThemes = 1,
        All = 2
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    public struct SyncUpdate
    {
        public string Key;
        public string Value;
        public SyncScope Scope;
        public long Timestamp;
    }

    public class PluginInterface : IDisposable
    {
        private const string NativeLibName = "omniglot_bridge_native";

        [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
        public delegate void OnSyncReceivedDelegate(SyncUpdate update);

        [DllImport(NativeLibName, CallingConvention = CallingConvention.Cdecl)]
        private static extern int Bridge_Initialize(string peerId, string meshSecret);

        [DllImport(NativeLibName, CallingConvention = CallingConvention.Cdecl)]
        private static extern void Bridge_Shutdown();

        [DllImport(NativeLibName, CallingConvention = CallingConvention.Cdecl)]
        private static extern int Bridge_PushUpdate(string key, string value, int scope);

        [DllImport(NativeLibName, CallingConvention = CallingConvention.Cdecl)]
        private static extern void Bridge_SetCallback(OnSyncReceivedDelegate callback);

        private OnSyncReceivedDelegate _managedCallback;
        private bool _isInitialized = false;

        public event Action<SyncUpdate> OnUpdateReceived;

        public PluginInterface(string peerId, string meshSecret)
        {
            int result = Bridge_Initialize(peerId, meshSecret);
            if (result != 0)
            {
                throw new Exception($"Failed to initialize OmniGlot Bridge. Error code: {result}");
            }

            _managedCallback = (update) =>
            {
                OnUpdateReceived?.Invoke(update);
                ApplyLocalChange(update);
            };

            Bridge_SetCallback(_managedCallback);
            _isInitialized = true;
        }

        public void SyncEnvironmentVariable(string key, string value)
        {
            if (!_isInitialized) throw new InvalidOperationException("Bridge not initialized.");
            
            Environment.SetEnvironmentVariable(key, value, EnvironmentVariableTarget.Process);
            Bridge_PushUpdate(key, value, (int)SyncScope.EnvironmentVariables);
        }

        public void SyncThemeProperty(string key, string value)
        {
            if (!_isInitialized) throw new InvalidOperationException("Bridge not initialized.");
            
            Bridge_PushUpdate(key, value, (int)SyncScope.UIThemes);
        }

        private void ApplyLocalChange(SyncUpdate update)
        {
            if (update.Scope == SyncScope.EnvironmentVariables)
            {
                Environment.SetEnvironmentVariable(update.Key, update.Value, EnvironmentVariableTarget.Process);
            }
        }

        public void Dispose()
        {
            if (_isInitialized)
            {
                Bridge_SetCallback(null);
                Bridge_Shutdown();
                _isInitialized = false;
            }
        }

        ~PluginInterface()
        {
            Dispose();
        }
    }
}